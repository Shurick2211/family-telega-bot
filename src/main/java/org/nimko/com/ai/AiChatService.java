package org.nimko.com.ai;

import static org.nimko.com.util.BotUtils.buildUserContentNew;

import java.util.List;
import org.nimko.com.config.AiChatProperties;
import org.nimko.com.util.BotUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.client.RestClient;

public class AiChatService {

  private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
  private final String transcriptionModel;

  private final AiChatProperties properties;
  private RestClient restClient;

  public AiChatService(final String transcriptionModel, final AiChatProperties properties) {
    this.transcriptionModel = transcriptionModel;
    this.properties = properties;
    restClient = getRestClient(properties, properties.apiKey());
  }

  private RestClient getRestClient(final AiChatProperties properties, final String key) {
    final RestClient restClient;
    restClient = properties.isConfigured()
        ? RestClient.builder()
        .baseUrl(properties.apiBaseUrl())
        .defaultHeader("Authorization", "Bearer " + key)
        .build()
        : null;
    return restClient;
  }

  public String ask(final String prompt) {
    return askInternal(prompt, null, null, false, null);
  }

  public String askNews(final String prompt) {
    log.info("Ask news!!!");
    if (properties.enableSecondary()) {
      restClient = getRestClient(properties, properties.apiKeySecondary());
    }
    final var result = askInternal(prompt, null, null, true, null);
    if (properties.enableSecondary()) {
      restClient = getRestClient(properties, properties.apiKey());
    }
    return result;
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

    try {
      final ChatCompletionResponse response = restClient.post()
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

    try {
      final ChatCompletionResponse response = restClient.post()
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
                                      double temperature) {

  }

  public record ChatMessage(String role, Object content) {

  }

  public record ChatCompletionResponse(List<Choice> choices) {

  }

  public record Choice(ChatMessage message) {

  }
}
