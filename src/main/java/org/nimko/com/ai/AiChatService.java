package org.nimko.com.ai;

import static org.nimko.com.util.BotUtils.buildUserContentNew;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.nimko.com.config.AiChatProperties;
import org.nimko.com.util.BotUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.client.RestClient;

public class AiChatService {

  private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
  private static final int MAX_TOKENS = 8000;
  private static final double SYSTEM_PROMPT_TEMPERATURE = 0.3d;
  private static final double TRANSLATION_TEMPERATURE = 0.2d;
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);
  private final String transcriptionModel;

  private final AiChatProperties properties;
  private final RestClient primaryRestClient;
  private final RestClient secondaryRestClient;

  public AiChatService(final String transcriptionModel, final AiChatProperties properties) {
    this.transcriptionModel = transcriptionModel;
    this.properties = properties;
    this.primaryRestClient = buildClient(properties, properties.apiKey());
    // The second key is also a quota reserve, not only the news key: it is built whenever
    // it is filled in, so a rate limited request can be retried on it. A key equal to the
    // primary one would hit the very same quota, so it is ignored.
    this.secondaryRestClient = StringUtils.isNotBlank(properties.apiKeySecondary())
        && !properties.apiKeySecondary().equals(properties.apiKey())
        ? buildClient(properties, properties.apiKeySecondary())
        : null;
  }

  private RestClient buildClient(final AiChatProperties properties, final String key) {
    if (!properties.isConfigured() || StringUtils.isBlank(key)) {
      return null;
    }
    return RestClient.builder()
        .baseUrl(properties.apiBaseUrl())
        .defaultHeader("Authorization", "Bearer " + key)
        .requestFactory(requestFactory())
        .build();
  }

  /**
   * Clients to try, in order. The first one is the key this kind of request normally uses;
   * the other key follows as a reserve for when every model is rate limited on the first.
   */
  private List<RestClient> resolveClients(final boolean news) {
    final RestClient preferred =
        news && properties.enableSecondary() && secondaryRestClient != null
            ? secondaryRestClient
            : primaryRestClient;
    final RestClient reserve =
        preferred == primaryRestClient ? secondaryRestClient : primaryRestClient;

    final List<RestClient> clients = new ArrayList<>();
    if (preferred != null) {
      clients.add(preferred);
    }
    if (reserve != null && reserve != preferred) {
      clients.add(reserve);
    }
    return clients;
  }

  private ClientHttpRequestFactory requestFactory() {
    final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }

  public String ask(final String prompt) {
    return askInternal(prompt, null, null, false, null);
  }

  public String askNews(final String prompt) {
    log.info("Ask news!!!");
    return askInternal(prompt, null, null, true, null);
  }

  public String askWithImage(final String prompt, final byte[] imageBytes, final String mimeType) {
    return askInternal(prompt, imageBytes, mimeType, false, null);
  }

  public String askNewsWithImage(final String prompt, final byte[] imageBytes,
      final String mimeType) {
    return askInternal(prompt, imageBytes, mimeType, true, null);
  }

  public String askArticle(final String prompt) {
    log.info("Ask article!!!");
    return askInternalWithSystem(prompt, BotUtils.articlesPrompt());
  }

  public String askDoc(final String prompt) {
    log.info("Ask doc!!!");
    return askInternalWithSystem(prompt, BotUtils.docPrompt());
  }

  /**
   * Literary translation request. Uses a low temperature so the translation stays natural but
   * faithful to the original text, without the model inventing anything.
   */
  public String askTranslate(final String systemPrompt, final String prompt) {
    return askInternalWithSystem(prompt, systemPrompt, TRANSLATION_TEMPERATURE);
  }

  private String askInternalWithSystem(final String prompt, final String systemPrompt) {
    return askInternalWithSystem(prompt, systemPrompt, SYSTEM_PROMPT_TEMPERATURE);
  }

  private String askInternalWithSystem(final String prompt, final String systemPrompt,
      final double temperature) {
    if (!properties.isConfigured()) {
      return "AI provider is not configured.";
    }

    final String userPrompt = StringUtils.isNotBlank(prompt) ? prompt.trim() : "";

    final List<RestClient> clients = resolveClients(false);
    if (clients.isEmpty()) {
      return "AI provider is not configured.";
    }

    return postWithModelFallback(clients, properties.defaultModel(), List.of(
        new ChatMessage("system", systemPrompt),
        new ChatMessage("user", buildUserContentNew(userPrompt, null, null))
    ), temperature);
  }

  public String transcribeAudio(final byte[] audioBytes, final String mimeType) {
    final var result = askInternal(
        "Transcribe this audio. Return only the transcribed text, nothing else.",
        audioBytes, mimeType, false, transcriptionModel);
    log.info("Transcribed audio: {}", result);
    return result.trim();
  }

  public String transcribeVideo(final byte[] videoBytes, final String mimeType) {
    final var result = askInternal(
        "Transcribe this video. Return only the transcribed text, nothing else.",
        videoBytes, mimeType, false, transcriptionModel);
    log.info("Transcribed video: {}", result);
    return result.trim();
  }

  private String defaultSystemPrompt() {
    return StringUtils.isNotBlank(properties.systemPrompt())
        ? properties.systemPrompt()
        : "You are a concise assistant inside a bot.";
  }

  private String askInternal(final String prompt, final byte[] imageBytes, final String mimeType,
      final boolean news, final String model) {
    if (!properties.isConfigured()) {
      return "AI provider is not configured.";
    }

    final double temperature = news ? 0.0d : 0.5d;
    final String userPrompt = StringUtils.isNotBlank(prompt)
        ? prompt.trim()
        : "Describe the image in detail.";

    final var currentModel = model != null ? model : properties.defaultModel();

    final List<RestClient> clients = resolveClients(news);
    if (clients.isEmpty()) {
      return "AI provider is not configured.";
    }

    return postWithModelFallback(clients, currentModel, List.of(
        new ChatMessage("system", news ? BotUtils.newsPrompt() : defaultSystemPrompt()),
        new ChatMessage("user", buildUserContentNew(userPrompt, imageBytes, mimeType))
    ), temperature);
  }


  /**
   * Sends the request, walking down the Gemini model chain while the provider reports a
   * rate limit, and switching to the other API key once every model is exhausted on the
   * current one. Any other failure stops the walk at once - retrying an older model or a
   * second key only makes sense for a quota problem.
   *
   * <p>Nothing is remembered between calls: as soon as a quota resets, the next request
   * goes back to the primary key and the configured model.
   */
  private String postWithModelFallback(final List<RestClient> clients, final String model,
      final List<ChatMessage> messages, final double temperature) {
    final List<String> chain = GeminiModelFallback.chainFor(model);

    for (int keyIndex = 0; keyIndex < clients.size(); keyIndex++) {
      final RestClient client = clients.get(keyIndex);
      final boolean hasReserveKey = keyIndex < clients.size() - 1;
      boolean keyExhausted = false;

      for (int i = 0; i < chain.size() && !keyExhausted; i++) {
        final String currentModel = chain.get(i);
        final boolean hasOlderModel = i < chain.size() - 1;

        try {
          return readContent(client.post()
              .uri("/chat/completions")
              .contentType(MediaType.APPLICATION_JSON)
              .body(new ChatCompletionRequest(currentModel, messages, temperature))
              .retrieve()
              .body(ChatCompletionResponse.class));
        } catch (final RuntimeException ex) {
          if (!GeminiModelFallback.isRateLimited(ex)) {
            log.error("Failed to query AI provider with model {}", currentModel, ex);
            return "AI provider request failed.";
          }

          if (hasOlderModel) {
            log.warn("Model {} is rate limited on API key #{}, retrying with {}",
                currentModel, keyIndex + 1, chain.get(i + 1));
          } else if (hasReserveKey) {
            log.warn("Every model is rate limited on API key #{}, switching to key #{}",
                keyIndex + 1, keyIndex + 2);
            keyExhausted = true;
          } else {
            log.error("Every model is rate limited on every configured API key", ex);
            return "AI provider request failed.";
          }
        }
      }
    }

    return "AI provider request failed.";
  }

  private String readContent(final ChatCompletionResponse response) {
    final String content =
        response == null || response.choices() == null || response.choices().isEmpty()
            ? null
            : BotUtils.extractContent(response.choices().get(0).message());

    if (StringUtils.isBlank(content)) {
      return "AI provider returned an empty response.";
    }

    return content.trim();
  }

  public record ChatCompletionRequest(String model, List<ChatMessage> messages,
                                      double temperature, int max_tokens) {

    public ChatCompletionRequest(final String model, final List<ChatMessage> messages,
        final double temperature) {
      this(model, messages, temperature, MAX_TOKENS);
    }
  }

  public record ChatMessage(String role, Object content) {

  }

  public record ChatCompletionResponse(List<Choice> choices) {

  }

  public record Choice(ChatMessage message) {

  }

}
