package org.nimko.com.ai;

import static org.nimko.com.util.BotUtils.buildUserContentNew;

import java.time.Duration;
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
    this.secondaryRestClient = properties.enableSecondary() && StringUtils.isNotBlank(properties.apiKeySecondary())
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

  private RestClient resolveClient(final boolean news) {
    if (news && properties.enableSecondary() && secondaryRestClient != null) {
      return secondaryRestClient;
    }
    return primaryRestClient;
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

  private String askInternalWithSystem(final String prompt, final String systemPrompt) {
    if (!properties.isConfigured()) {
      return "AI provider is not configured.";
    }

    final double temperature = 0.3d;
    final String userPrompt = StringUtils.isNotBlank(prompt) ? prompt.trim() : "";
    final var currentModel = properties.defaultModel();

    final ChatCompletionRequest request = new ChatCompletionRequest(
        currentModel,
        List.of(
            new ChatMessage("system", systemPrompt),
            new ChatMessage("user", buildUserContentNew(userPrompt, null, null))
        ),
        temperature);

    final RestClient client = resolveClient(false);
    if (client == null) {
      return "AI provider is not configured.";
    }

    try {
      final ChatCompletionResponse response = client.post()
          .uri("/chat/completions")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(ChatCompletionResponse.class);

      final String content =
          response == null || response.choices() == null || response.choices().isEmpty()
              ? null
              : BotUtils.extractContent(response.choices().get(0).message());

      if (StringUtils.isBlank(content)) {
        return "AI provider returned an empty response.";
      }

      return content.trim();
    } catch (final RuntimeException ex) {
      log.error("Failed to query AI provider with custom system prompt", ex);
      return "AI provider request failed.";
    }
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
        : "You are a concise assistant inside a Telegram bot.";
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

    final ChatCompletionRequest request = new ChatCompletionRequest(
        currentModel,
        List.of(
            new ChatMessage("system", news ? BotUtils.newsPrompt() : defaultSystemPrompt()),
            new ChatMessage("user", buildUserContentNew(userPrompt, imageBytes, mimeType))
        ),
        temperature);

    final RestClient client = resolveClient(news);
    if (client == null) {
      return "AI provider is not configured.";
    }

    try {
      final ChatCompletionResponse response = client.post()
          .uri("/chat/completions")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(ChatCompletionResponse.class);

      final String content =
          response == null || response.choices() == null || response.choices().isEmpty()
              ? null
              : BotUtils.extractContent(response.choices().get(0).message());

      if (StringUtils.isBlank(content)) {
        return "AI provider returned an empty response.";
      }

      return content.trim();
    } catch (final RuntimeException ex) {
      log.error("Failed to query AI provider", ex);
      return "AI provider request failed.";
    }
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
