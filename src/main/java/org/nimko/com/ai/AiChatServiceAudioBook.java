package org.nimko.com.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.function.Consumer;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.nimko.com.config.AiChatProperties;
import org.nimko.com.services.AudioConverter;
import org.nimko.com.util.BotUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.client.RestClient;

public class AiChatServiceAudioBook {

  private static final Logger log = LoggerFactory.getLogger(AiChatServiceAudioBook.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .setSerializationInclusion(JsonInclude.Include.NON_NULL)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String DEFAULT_TTS_VOICE = "Puck";
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);
  private final String ttsModel;
  private final String ttsVoice;
  private final String transcriptionModel;
  private final AiChatProperties properties;
  private final RestClient restClient;
  private final AudioConverter audioConverter;

  public AiChatServiceAudioBook(final String ttsModel, final String ttsVoice,
      final String transcriptionModel, final AiChatProperties properties, final AudioConverter audioConverter) {
    this.ttsModel = ttsModel;
    this.ttsVoice = ttsVoice;
    this.transcriptionModel = transcriptionModel;
    this.properties = properties;
    this.audioConverter = audioConverter;
    this.restClient = buildClient(properties, properties.apiKey());
  }

  private RestClient buildClient(final AiChatProperties properties, final String key) {
    if (!properties.isConfigured() || StringUtils.isBlank(key)) {
      return null;
    }
    String baseUrl = properties.apiBaseUrl();
    if (baseUrl != null) {
      baseUrl = baseUrl.replaceAll("/openai/?$", "");
    }
    return RestClient.builder()
        .baseUrl(baseUrl)
        .defaultHeader("x-goog-api-key", key)
        .requestFactory(requestFactory())
        .build();
  }

  private ClientHttpRequestFactory requestFactory() {
    final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }

  public byte[] narrateBook(final String bookText, final Consumer<Integer> progressCallback) {
    log.info("Narrate book!!!");
    if (!properties.isConfigured() || StringUtils.isBlank(ttsModel) || StringUtils.isBlank(bookText)) {
      return null;
    }

    final List<String> chunks = splitIntoChunks(bookText, 1000);
    log.info("Split book text into {} chunks for narration", chunks.size());

    final ExecutorService executor = Executors.newFixedThreadPool(3);
    final AtomicInteger completedChunks = new AtomicInteger(0);

    final List<CompletableFuture<byte[]>> futures = new java.util.ArrayList<>();

    for (int i = 0; i < chunks.size(); i++) {
      final int chunkIndex = i;
      final String chunk = chunks.get(i);
      
      final CompletableFuture<byte[]> future = CompletableFuture.supplyAsync(() -> {
        log.info("Narrating chunk {}/{} (length: {})", chunkIndex + 1, chunks.size(), chunk.length());
        
        byte[] audioBytes = narrateSingleChunk(chunk);
        if (audioBytes == null || audioBytes.length == 0) {
          log.warn("Chunk {}/{} narration was empty or blocked. Attempting to sanitize and retry...", chunkIndex + 1, chunks.size());
          final String sanitizedText = sanitizeTextForNarration(chunk);
          if (StringUtils.isNotBlank(sanitizedText)) {
            audioBytes = narrateSingleChunk(sanitizedText);
          }
        }
        
        if (audioBytes == null || audioBytes.length == 0) {
          log.warn("Chunk {}/{} narration failed after sanitization. Skipping this chunk.", chunkIndex + 1, chunks.size());
        }

        if (progressCallback != null) {
            final int progress = (int) (((double) completedChunks.incrementAndGet() / chunks.size()) * 100);
            progressCallback.accept(progress);
        }
        
        return audioBytes;
      }, executor);
      
      futures.add(future);
    }

    final List<byte[]> audioChunks = new java.util.ArrayList<>();
    for (int i = 0; i < futures.size(); i++) {
        try {
            final byte[] audioBytes = futures.get(i).join();
            if (audioBytes != null && audioBytes.length > 0) {
                audioChunks.add(audioBytes);
            }
        } catch (final Exception ex) {
            log.error("Failed to narrate chunk {}", i + 1, ex);
        }
    }
    
    executor.shutdown();

    if (audioChunks.isEmpty()) {
      log.error("All chunks failed to narrate.");
      return null;
    }

    log.info("Successfully narrated {}/{} chunks. Concatenating audio...", audioChunks.size(), chunks.size());
    return audioConverter.concatenatePcmAndConvertToMp3(audioChunks, 24000, 1);
  }

  private String sanitizeTextForNarration(final String text) {
    final String prompt = "Rewrite the following book text to be completely safe, mild, and free of any potential safety triggers (such as intense violence, horror, or sensitive words), while fully preserving the plot, characters, and narrative flow for audio narration. Return ONLY the rewritten text, nothing else:\n\n" + text.trim();
    
    final SystemInstruction sys = new SystemInstruction(List.of(new Part("You are a concise assistant inside a bot.", null)));
    final Content content = new Content("user", List.of(new Part(prompt, null)));
    final GenerateContentRequest request = new GenerateContentRequest(sys, List.of(content), new GenerationConfig(0.3, null, null, null));
    
    if (restClient == null) {
      return null;
    }

    try {
      final String rawResponse = restClient.post()
          .uri("/models/" + properties.defaultModel() + ":generateContent")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(String.class);

      final String sanitized = extractText(rawResponse);
      if (StringUtils.isNotBlank(sanitized) && !sanitized.contains("AI provider request failed")) {
        return sanitized.trim();
      }
    } catch (final Exception ex) {
      log.error("Failed to sanitize text for narration", ex);
    }
    return null;
  }

  private byte[] narrateSingleChunk(final String chunkText) {
    if (restClient == null || StringUtils.isBlank(chunkText)) {
      return null;
    }

    final List<RoleSegment> segments = breakDownTextByRoles(chunkText);
    if (segments == null || segments.isEmpty()) {
      log.warn("Failed to break down text by roles, falling back to single voice narrator.");
      return narrateWithVoice(chunkText, getVoiceForRole("NARRATOR"));
    }

    final List<byte[]> segmentAudioChunks = new java.util.ArrayList<>();
    for (final RoleSegment segment : segments) {
      if (StringUtils.isBlank(segment.text())) {
        continue;
      }
      final String voice = getVoiceForRole(segment.role());
      final byte[] audio = narrateWithVoice(segment.text(), voice);
      if (audio != null && audio.length > 0) {
        segmentAudioChunks.add(audio);
      }
    }

    if (segmentAudioChunks.isEmpty()) {
      return null;
    }

    if (segmentAudioChunks.size() == 1) {
      return segmentAudioChunks.get(0);
    }

    return audioConverter.concatenatePcmAndConvertToWav(segmentAudioChunks, 24000, 1);
  }

  private String getVoiceForRole(final String role) {
    if (StringUtils.isBlank(role)) {
      return StringUtils.isNotBlank(ttsVoice) ? ttsVoice : DEFAULT_TTS_VOICE;
    }
    return switch (role.toUpperCase()) {
      case "MALE" -> "Charon";
      case "FEMALE" -> "Kore";
      case "NARRATOR" -> StringUtils.isNotBlank(ttsVoice) ? ttsVoice : DEFAULT_TTS_VOICE;
      default -> StringUtils.isNotBlank(ttsVoice) ? ttsVoice : DEFAULT_TTS_VOICE;
    };
  }

  private List<RoleSegment> breakDownTextByRoles(final String text) {
    final String prompt = BotUtils.rolesPrompt() + "\n\n" + text.trim();
    
    final Content content = new Content("user", List.of(new Part(prompt, null)));
    
    final String model = StringUtils.isNotBlank(transcriptionModel) ? transcriptionModel : 
        (properties.defaultModel() != null ? properties.defaultModel() : "gemini-3.5-flash");
    
    final GenerateContentRequest request = new GenerateContentRequest(
        null,
        List.of(content),
        new GenerationConfig(0.1, null, null, "application/json")
    );

    try {
      final String rawResponse = restClient.post()
          .uri("/models/" + model + ":generateContent")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(String.class);

      String jsonText = extractText(rawResponse);
      if (StringUtils.isNotBlank(jsonText)) {
        jsonText = jsonText.trim();
        if (jsonText.startsWith("```json")) {
            jsonText = jsonText.substring(7);
        } else if (jsonText.startsWith("```")) {
            jsonText = jsonText.substring(3);
        }
        if (jsonText.endsWith("```")) {
            jsonText = jsonText.substring(0, jsonText.length() - 3);
        }
        jsonText = jsonText.trim();
        
        return OBJECT_MAPPER.readValue(jsonText, new TypeReference<List<RoleSegment>>() {});
      } else {
        log.warn("Text analysis for roles returned empty response.");
      }
    } catch (final Exception ex) {
      log.error("Failed to analyze text for roles", ex);
    }
    return null;
  }

  private byte[] narrateWithVoice(final String chunkText, String voiceName) {
    if (StringUtils.isBlank(voiceName)) {
      voiceName = DEFAULT_TTS_VOICE;
    }
    voiceName = StringUtils.capitalize(voiceName);

    final String prompt = chunkText.trim();
    
    final Content content = new Content("user", List.of(new Part(prompt, null)));
    final GenerationConfig config = new GenerationConfig(
        null,
        List.of("AUDIO"),
        new SpeechConfig(new VoiceConfig(new PrebuiltVoiceConfig(voiceName))),
        null
    );

    final GenerateContentRequest request = new GenerateContentRequest(
        null,
        List.of(content),
        config
    );

    try {
      final String rawResponse = restClient.post()
          .uri("/models/" + ttsModel + ":generateContent")
          .contentType(MediaType.APPLICATION_JSON)
          .body(request)
          .retrieve()
          .body(String.class);

      final byte[] audioBytes = extractAudioBytes(rawResponse);
      if (audioBytes == null || audioBytes.length == 0) {
        log.warn("TTS provider returned no audio data for voice {}.", voiceName);
        return null;
      }

      return audioBytes;
    } catch (final RuntimeException ex) {
      log.error("Failed to query TTS provider for segment with voice {}", voiceName, ex);
      return null;
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record RoleSegment(String role, String text) {}

  private List<String> splitIntoChunks(final String text, final int maxChunkSize) {
    final List<String> chunks = new java.util.ArrayList<>();
    if (StringUtils.isBlank(text)) {
      return chunks;
    }

    final String[] paragraphs = text.split("\n+");
    final StringBuilder currentChunk = new StringBuilder();

    for (final String paragraph : paragraphs) {
      final String trimmedParagraph = paragraph.trim();
      if (trimmedParagraph.isEmpty()) {
        continue;
      }
      if (currentChunk.length() + trimmedParagraph.length() < maxChunkSize) {
        if (currentChunk.length() > 0) {
          currentChunk.append("\n\n");
        }
        currentChunk.append(trimmedParagraph);
      } else {
        if (currentChunk.length() > 0) {
          chunks.add(currentChunk.toString());
          currentChunk.setLength(0);
        }
        if (trimmedParagraph.length() < maxChunkSize) {
          currentChunk.append(trimmedParagraph);
        } else {
          final String[] sentences = trimmedParagraph.split("(?<=[.!?])\\s+");
          for (final String sentence : sentences) {
            final String trimmedSentence = sentence.trim();
            if (trimmedSentence.isEmpty()) {
              continue;
            }
            if (currentChunk.length() + trimmedSentence.length() < maxChunkSize) {
              if (currentChunk.length() > 0) {
                currentChunk.append(" ");
              }
              currentChunk.append(trimmedSentence);
            } else {
              if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString());
                currentChunk.setLength(0);
              }
              currentChunk.append(trimmedSentence);
            }
          }
        }
      }
    }
    if (currentChunk.length() > 0) {
      chunks.add(currentChunk.toString());
    }
    return chunks;
  }

  private byte[] extractAudioBytes(final String rawResponse) {
    if (StringUtils.isBlank(rawResponse)) {
      return null;
    }
    try {
      final JsonNode root = OBJECT_MAPPER.readTree(rawResponse);
      final JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
      for (final JsonNode part : parts) {
          if (part.has("inlineData")) {
              final JsonNode inlineData = part.get("inlineData");
              final String data = inlineData.path("data").asText(null);
              if (StringUtils.isNotBlank(data)) {
                  return Base64.getDecoder().decode(data);
              }
          }
      }
    } catch (final Exception ex) {
      log.error("Failed to parse TTS provider response", ex);
    }
    return null;
  }
  
  private String extractText(final String rawResponse) {
    if (StringUtils.isBlank(rawResponse)) {
      return null;
    }
    try {
        final JsonNode root = OBJECT_MAPPER.readTree(rawResponse);
        final JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        for (final JsonNode part : parts) {
            if (part.has("text")) {
                final String text = part.get("text").asText();
                if (StringUtils.isNotBlank(text)) {
                    return text.trim();
                }
            }
        }
    } catch (final Exception e) {
        log.error("Failed to parse AI response text", e);
    }
    return null;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record GenerateContentRequest(
      SystemInstruction systemInstruction,
      List<Content> contents,
      GenerationConfig generationConfig) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SystemInstruction(List<Part> parts) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Content(String role, List<Part> parts) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Part(String text, InlineData inlineData) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record InlineData(String mimeType, String data) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record GenerationConfig(
      Double temperature,
      List<String> responseModalities,
      SpeechConfig speechConfig,
      String responseMimeType) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SpeechConfig(VoiceConfig voiceConfig) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record VoiceConfig(PrebuiltVoiceConfig prebuiltVoiceConfig) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record PrebuiltVoiceConfig(String voiceName) {}
}
