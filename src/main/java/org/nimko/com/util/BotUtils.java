package org.nimko.com.util;

import static org.nimko.com.util.ReadResourceUtils.readResourceFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.nimko.com.ai.AiChatService.ChatMessage;
import org.nimko.com.entity.ChatContextEntity;
import org.nimko.com.repository.ChatContextRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

@UtilityClass
public final class BotUtils {

  private static final Logger log = LoggerFactory.getLogger(BotUtils.class);
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();
  private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)\\]>\"']+");

  public static final String[] BOT_NAMES = {"айріс", "айрис", "iris", "бот"};
  public static final String ADDITIONAL_INFO = """
      \n
      (Примітка: Нам не вдалося автоматично завантажити вміст цих посилань.
      Будь ласка, спробуй самостійно отримати/знайти інформацію за цими посиланнями за
      допомогою своїх інструментів пошуку/доступу до веб-сторінок, або напиши статтю на
      основі наявного тексту та контексту посилання.)""";

  public static String normalizeCommand(final String text) {
    if (StringUtils.isBlank(text)) {
      return text;
    }
    final int separatorIndex = indexOfWhitespace(text);
    final String command = separatorIndex >= 0 ? text.substring(0, separatorIndex) : text;
    final int botIndex = command.indexOf('@');
    return botIndex >= 0 ? command.substring(0, botIndex) : command;
  }

  public static boolean startsWithBotPrefix(final String text) {
    if (StringUtils.isBlank(text)) {
      return false;
    }
    return text.length() >= 3 && (text.regionMatches(true, 0, "бот", 0, 3)
        || text.regionMatches(true, 0, "iris", 0, 4)
        || text.regionMatches(true, 0, "айрис", 0, 5)
        || text.regionMatches(true, 0, "айріс", 0, 5));
  }

  public static String stripTextPrefix(final String text) {
    if (StringUtils.isBlank(text)) {
      return text;
    }
    final String trimmed = text.trim();
    final String lower = trimmed.toLowerCase();

    for (final String prefix : BOT_NAMES) {
      if (lower.startsWith(prefix)) {
        String cut = trimmed.substring(prefix.length()).trim();
        if (cut.startsWith(",") || cut.startsWith(":")) {
          cut = cut.substring(1).trim();
        }
        return cut;
      }
    }
    return trimmed;
  }

  public static String stripBotPrefix(final String text, final String botUsername,
      final List<String> context, final boolean groupChat) {
    if (StringUtils.isBlank(text)) {
      return text;
    }

    String prompt = text.trim();

    if (StringUtils.isNotBlank(botUsername)) {
      final String mention = "@" + botUsername;
      if (prompt.regionMatches(true, 0, mention, 0, mention.length())) {
        prompt = prompt.substring(mention.length()).trim();
      } else {
        prompt = stripTextPrefix(prompt);
      }
    } else {
      prompt = stripTextPrefix(prompt);
    }

    if (groupChat) {
      final String contextStr = (context != null && !context.isEmpty())
          ? String.join(", ", context)
          : "";
      prompt = String.format(
          "Based on the following chat_context that is a list of messages in JSON format from group chat,"
              + " execute the prompt: chat_context=[%s], prompt=%s",
          contextStr, prompt);
    }

    return prompt;
  }

  public static String removeUrls(final String text, final List<String> urls) {
    if (StringUtils.isBlank(text)) {
      return text;
    }
    String cleaned = text;
    if (urls != null) {
      for (final String url : urls) {
        cleaned = cleaned.replace(url, " ");
      }
    }
    return cleaned.replaceAll("\\s{2,}", " ").trim();
  }

  public static String newsPrompt() {
    return readResourceFile("prompts/news_prompt.txt");
  }

  public static String articlesPrompt() {
    return readResourceFile("prompts/articles_prompt.txt");
  }

  public static String docPrompt() {
    return readResourceFile("prompts/doc_prompt.txt");
  }

  public static String bookPrompt() {
    return readResourceFile("prompts/book_prompt.txt");
  }

  public static String rolesPrompt() {
    return readResourceFile("prompts/roles_prompt.txt");
  }

  public static String extractContent(final ChatMessage message) {
    if (message == null || message.content() == null) {
      return null;
    }
    if (message.content() instanceof final String content) {
      return content;
    }
    return message.content().toString();
  }

  public static String extractReadableText(final String html) {
    if (StringUtils.isBlank(html)) {
      return null;
    }
    String cleaned = html
        .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
        .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
        .replaceAll("(?is)<noscript[^>]*>.*?</noscript>", " ")
        .replaceAll("(?is)<[^>]+>", " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'");

    cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();
    return cleaned;
  }

  public static String stripTrailingPunctuation(final String value) {
    if (StringUtils.isBlank(value)) {
      return value;
    }
    int endIndex = value.length();
    while (endIndex > 0 && ".,!?;:)\"'»”".contains(String.valueOf(value.charAt(endIndex - 1)))) {
      endIndex--;
    }
    return value.substring(0, endIndex);
  }

  public static String truncate(final String value, final int maxLength) {
    if (StringUtils.isBlank(value) || maxLength <= 0 || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength).trim();
  }

  public static String extractCommandPayload(final String text) {
    if (StringUtils.isBlank(text)) {
      return "";
    }
    final int separatorIndex = indexOfWhitespace(text);
    return separatorIndex >= 0 ? text.substring(separatorIndex + 1).trim() : "";
  }

  private static int indexOfWhitespace(final String text) {
    for (int i = 0; i < text.length(); i++) {
      if (Character.isWhitespace(text.charAt(i))) {
        return i;
      }
    }
    return -1;
  }

  public static boolean isAddressedToBot(final String text, final String botUsername) {
    if (StringUtils.isBlank(text)) {
      return false;
    }
    final String trimmed = text.trim();
    if (StringUtils.isNotBlank(botUsername)) {
      final String mention = "@" + botUsername;
      if (trimmed.regionMatches(true, 0, mention, 0, mention.length())) {
        return true;
      }
    }
    return startsWithBotPrefix(trimmed);
  }

  public static String prepareNewsPrompt(final String prompt) {
    if (StringUtils.isBlank(prompt)) {
      return prompt;
    }
    final String trimmed = prompt.trim();
    final List<String> urls = extractUrls(trimmed);
    if (urls.isEmpty()) {
      return trimmed;
    }
    final String newsWithoutUrls = removeUrls(trimmed, urls);
    final String basePrompt = StringUtils.isNotBlank(newsWithoutUrls)
        ? newsWithoutUrls
        : "Перепиши новину за інформацією з посилання.";

    final String linkContext = buildLinkContext(urls);
    final String urlsListStr = String.join(", ", urls);

    if (StringUtils.isBlank(linkContext)) {
      return newsPrompt()
          + "\n\n"
          + basePrompt
          + "\n\nПосилання для обробки: " + urlsListStr
          + ADDITIONAL_INFO;
    }

    return newsPrompt()
        + "\n\n"
        + basePrompt
        + "\n\nПосилання для обробки: " + urlsListStr
        + "\n\nІнформація з посилань:\n"
        + linkContext;
  }

  public static String prepareArticlesPrompt(final String prompt) {
    if (StringUtils.isBlank(prompt)) {
      return prompt;
    }
    final String trimmed = prompt.trim();
    final List<String> urls = extractUrls(trimmed);
    if (urls.isEmpty()) {
      return trimmed;
    }
    final String textWithoutUrls = removeUrls(trimmed, urls);
    final String basePrompt = StringUtils.isNotBlank(textWithoutUrls)
        ? textWithoutUrls
        : "Напиши розгорнуту статтю за інформацією з посилання.";

    final String linkContext = buildLinkContext(urls);
    final String urlsListStr = String.join(", ", urls);

    if (StringUtils.isBlank(linkContext)) {
      return articlesPrompt()
          + "\n\n"
          + basePrompt
          + "\n\nПосилання для обробки: " + urlsListStr
          + ADDITIONAL_INFO;
    }

    return articlesPrompt()
        + "\n\n"
        + basePrompt
        + "\n\nПосилання для обробки: " + urlsListStr
        + "\n\nІнформація з посилань:\n"
        + linkContext;
  }

  public static List<String> extractUrls(final String text) {
    final List<String> urls = new ArrayList<>();
    final Matcher matcher = URL_PATTERN.matcher(text);
    while (matcher.find()) {
      final String url = stripTrailingPunctuation(matcher.group());
      if (StringUtils.isNotBlank(url)) {
        urls.add(url);
      }
    }
    return urls;
  }

  /**
   * Returns true if text contains a URL that looks like TikTok, YouTube or Instagram link.
   */
  public static boolean containsMediaUrl(final String text) {
    if (StringUtils.isBlank(text)) {
      return false;
    }
    final List<String> urls = extractUrls(text);
    for (final String u : urls) {
      final String lower = u.toLowerCase();
      if (lower.contains("tiktok.com") || lower.contains("youtube.com")
          || lower.contains("youtu.be") || lower.contains("instagram.com") || lower.contains(
          "instagr.am")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Extracts the first media URL (tiktok/youtube/instagram) from text or null if none.
   */
  public static String extractFirstUrl(final String text) {
    if (StringUtils.isBlank(text)) {
      return null;
    }
    final List<String> urls = extractUrls(text);
    for (final String u : urls) {
      final String lower = u.toLowerCase();
      if (lower.contains("tiktok.com") || lower.contains("youtube.com")
          || lower.contains("youtu.be") || lower.contains("instagram.com") || lower.contains(
          "instagr.am")) {
        return u;
      }
    }
    return urls.isEmpty() ? null : urls.get(0);
  }

  public static String buildLinkContext(final List<String> urls) {
    final StringBuilder builder = new StringBuilder();
    for (final String url : urls) {
      final String linkContext = fetchLinkContext(url);
      if (StringUtils.isBlank(linkContext)) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append("\n\n");
      }
      builder.append("Посилання: ").append(url).append("\n").append(linkContext);
    }
    return builder.length() > 0 ? builder.toString() : null;
  }

  public static String fetchLinkContext(final String url) {
    try {
      final HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .timeout(Duration.ofSeconds(10))
          .header("User-Agent",
              "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
          .header("Accept",
              "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
          .header("Accept-Language", "en-US,en;q=0.5")
          .GET()
          .build();

      final HttpResponse<String> response = HTTP_CLIENT.send(request,
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300 || StringUtils.isBlank(
          response.body())) {
        return null;
      }
      return truncate(extractReadableText(response.body()), 3000);
    } catch (final Exception ex) {
      log.warn("Failed to fetch link context from {}", url, ex);
      return null;
    }
  }

  public static Object buildUserContentNew(final String prompt, final byte[] mediaBytes,
      final String mimeType) {
    if (mediaBytes == null || mediaBytes.length == 0) {
      return prompt;
    }

    final String resolvedMimeType = StringUtils.isNotBlank(mimeType) ? mimeType : "image/jpeg";
    final String base64Data = Base64.getEncoder().encodeToString(mediaBytes);

    if (resolvedMimeType.startsWith("audio/")) {
      final String audioFormat = resolvedMimeType.substring("audio/".length());
      return List.of(
          Map.of("type", "text", "text", prompt),
          Map.of("type", "input_audio", "input_audio",
              Map.of("data", base64Data, "format", audioFormat)));
    }

    final String dataUrl = "data:" + resolvedMimeType + ";base64," + base64Data;
    return List.of(
        Map.of("type", "text", "text", prompt),
        Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
  }

  public static void addTranscribedInContext(final String botUser, final String username,
      final String transcribed,
      final Long chatId, final int messageId, final ChatContextRepository chatContextRepository,
      final boolean groupChat) {
    log.info("Saved context for {}", username);
    final var entity = new ChatContextEntity()
        .setChatId(chatId)
        .setUserName(botUser)
        .setName(username)
        .setMessageId(messageId)
        .setMessage(transcribed)
        .setGroupChat(groupChat);
    chatContextRepository.save(entity);
  }

  public static Locale resolveLocale(final String langCode) {
    if (langCode == null) {
      return Locale.forLanguageTag("uk");
    }
    final String lower = langCode.toLowerCase();
    if (lower.startsWith("uk") || lower.startsWith("ua")) {
      return Locale.forLanguageTag("uk");
    } else if (lower.startsWith("ru")) {
      return Locale.forLanguageTag("ru");
    } else if (lower.startsWith("en")) {
      return Locale.forLanguageTag("en");
    } else if (lower.startsWith("sk")) {
      return Locale.forLanguageTag("sk");
    }
    return Locale.forLanguageTag("uk");
  }

  public static String detectGroupLanguage(final String currentText, final List<String> history) {
    int ukCount = 0;
    int ruCount = 0;

    if (StringUtils.isNotBlank(currentText)) {
      ukCount += countSpecificChars(currentText, "іїєґІЇЄҐ");
      ruCount += countSpecificChars(currentText, "ыэъёЫЭЪЁ");
    }

    if (ukCount == 0 && ruCount == 0 && history != null) {
      for (final String histJson : history) {
        ukCount += countSpecificChars(histJson, "іїєґІЇЄҐ");
        ruCount += countSpecificChars(histJson, "ыэъёЫЭЪЁ");
      }
    }

    if (ukCount > ruCount) {
      return "uk";
    } else if (ruCount > ukCount) {
      return "ru";
    }
    return null;
  }

  private static int countSpecificChars(final String text, final String charsToCount) {
    int count = 0;
    for (int i = 0; i < text.length(); i++) {
      if (charsToCount.indexOf(text.charAt(i)) >= 0) {
        count++;
      }
    }
    return count;
  }
}
