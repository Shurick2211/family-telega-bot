package org.nimko.com.util;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nimko.com.ai.AiChatService.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.message.Message;

public final class BotUtils {

  private static final Logger log = LoggerFactory.getLogger(BotUtils.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();
  private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)\\]>\"']+");
  private static final ConcurrentMap<String, ReplyPayload> COPY_IMG_PAYLOADS = new ConcurrentHashMap<>();
  public static final int TELEGRAM_CAPTION_LIMIT = 1024;
  public static final String NEWS_LINK_INSTRUCTION = """
      Якщо у новинному тексті є посилання на зовнішній ресурс, спочатку отримай інформацію саме з нього.
      Потім перепиши новину стандартним новинним стилем: нейтрально, без вигадок і з повним збереженням фактів.
      """;
  public static final String ARTICLES_LINK_INSTRUCTION = """
      Якщо у тексті для статті є посилання на зовнішній ресурс, спочатку отримай інформацію саме з нього.
      Потім напиши розгорнуту та інформативну статтю на основі отриманих даних.
      """;
  public static final int MAX_CONTEXT_SIZE = 50;

  private BotUtils() {
  }

  public static boolean hasUserContent(final Message message) {
    return message != null
        && (message.hasText() || StringUtils.hasText(message.getCaption())
        || message.hasPhoto() || message.hasVoice() || message.hasAudio()
        || message.hasVideoNote());
  }

  public static String resolveIncomingText(final Message message) {
    if (message == null) {
      return null;
    }
    if (message.hasText()) {
      return message.getText();
    }
    if (StringUtils.hasText(message.getCaption())) {
      return message.getCaption();
    }
    return null;
  }

  public static String normalizeCommand(final String text) {
    if (!StringUtils.hasText(text)) {
      return text;
    }
    final int spaceIndex = text.indexOf(' ');
    final String command = spaceIndex >= 0 ? text.substring(0, spaceIndex) : text;
    final int botIndex = command.indexOf('@');
    return botIndex >= 0 ? command.substring(0, botIndex) : command;
  }

  public static boolean isGroupChat(final Message message) {
    return message != null
        && message.getChat() != null
        && (Boolean.TRUE.equals(message.getChat().isGroupChat())
        || Boolean.TRUE.equals(message.getChat().isSuperGroupChat()));
  }

  public static boolean startsWithBotPrefix(final String text) {
    if (!StringUtils.hasText(text)) {
      return false;
    }
    return text.length() >= 3 && (text.regionMatches(true, 0, "бот", 0, 3)
        || text.regionMatches(true, 0, "iris", 0, 4)
        || text.regionMatches(true, 0, "айрис", 0, 5)
        || text.regionMatches(true, 0, "айріс", 0, 5));
  }

  public static String stripTextPrefix(final String text) {
    if (!StringUtils.hasText(text)) {
      return text;
    }
    final String trimmed = text.trim();
    final String lower = trimmed.toLowerCase();

    final String[] prefixes = {"айріс", "айрис", "iris", "бот"};
    for (final String prefix : prefixes) {
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
    if (!StringUtils.hasText(text)) {
      return text;
    }

    String prompt = text.trim();

    if (StringUtils.hasText(botUsername)) {
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
    if (!StringUtils.hasText(text)) {
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
    return """
        Ти — професійний редактор новин для Telegram.
        
        Твоє завдання — переписати наданий текст українською мовою так, щоб він виглядав як оригінальна новина, написана українським редактором.
        
        Дотримуйся таких правил:
        
        • Пиши нейтральним інформаційним стилем без емоційної лексики, оцінок, припущень і власних висновків.
        • Повністю зберігай зміст, логіку та послідовність подій.
        • Не змінюй факти й не додавай інформації, якої немає в оригіналі.
        • Обов'язково зберігай усі цифри, дати, суми, відсотки, імена, назви організацій, географічні назви та інші важливі факти.
        • Якщо інформація подається з посиланням на ЗМІ, офіційний орган або посадову особу, обов'язково залишай атрибуцію («повідомляє Reuters», «за даними Financial Times», «заявив…» тощо), але прибирай усі посилання.
        • Якщо новина містить цитати, залишай лише найважливіші. Довгі цитати скорочуй без втрати змісту.
        • Не використовуй клікбейтні заголовки.
        • Не використовуй русизми, кальки з російської мови, канцеляризми та неприродні конструкції. Текст має звучати природно українською мовою.
        • Не копіюй структуру речень з оригіналу. Перебудовуй текст так, щоб він легко читався українською.
        • Якщо в тексті є повтори або другорядні подробиці, прибирай їх, якщо це не впливає на розуміння новини.
        • Видаляй усі URL-адреси, хештеги, рекламні вставки, службові повідомлення, заклики підписатися, назви Telegram-каналів, інформацію про рекламу, кнопки «Читайте також», «Надіслати новину» та будь-які інші зайві елементи.
        • Не додавай наприкінці згадку про джерело, якщо вона не є частиною самої новини.
        • Не використовуй емодії.
        
        Формат відповіді:
        • короткий інформативний заголовок;
        • 2–4 короткі абзаци;
        • загальний обсяг приблизно 500–700 символів. Якщо новину можна зробити коротшою без втрати важливої інформації — скорочуй її максимально.
        
        Мета — отримати готову до публікації в Telegram новину, яка виглядає як авторський матеріал української редакції, але повністю зберігає зміст першоджерела.
        Всі заголовки та ключові слова зроби жирним(bold) шрифтом
        """;
  }

  public static String articlesPrompt() {
    return """
        Ти — професійний копірайтер, журналіст та редактор.
        
        Твоє завдання — написати повнорозмірну, структуровану, глибоку та цікаву статтю українською мовою на основі наданої інформації або посилань.
        
        Дотримуйся таких правил:
        • Пиши професійним, аналітичним, але водночас доступним і захоплюючим стилем.
        • Структуруй статтю за допомогою чітких заголовків та підзаголовків.
        • Логічно розділяй текст на абзаци, використовуй марковані та нумеровані списки для покращення читабельності.
        • Текст має бути зв'язним, логічним і послідовним. Уникай занадто коротких, обірваних речень.
        • Повністю розкривай тему, додавай контекст, пояснюй складні терміни, якщо вони є.
        • Зберігай та інтегруй усі ключові факти, цифри, дати, назви, імена та статистику.
        • Мова статті має бути бездоганною: природна українська без русизмів, кальок чи канцеляризмів.
        • Зроби вступ (цікавий лід), основну частину (розбиту на тематичні блоки) та логічний висновок.
        • Використовуй виділення жирним шрифтом (bold) за допомогою **подвійних зірочок** (наприклад, **важливе слово**) для найважливіших думок та ключових понять.
        • Обов'язково видаляй усі рекламні заклики, посилання на сторонні ресурси, заклики підписатися тощо.
        • Ніякі факти самостійно не вигадуй, використовуй існуючі з наданого матеріалу.
        • Довжина статі не має перевищувати 10000 символів з пробілами.
        • В кінці обов'язково додай посилання на першоджерело.
        
        Формат відповіді:
        • Почни статтю з головного заголовка, використовуючи префікс '# ' (наприклад: # Головний заголовок);
        • Використовуй підзаголовки з префіксом '## ' або '### ';
        • Кілька тематичних розділів;
        • Висновок або підсумок.
        
        Мета — створити повноцінний, професійно оформлений матеріал, готовий до публікації чи читання у вигляді документу.
        """;
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
    if (!StringUtils.hasText(html)) {
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
    if (!StringUtils.hasText(value)) {
      return value;
    }
    int endIndex = value.length();
    while (endIndex > 0 && ".,!?;:)\"'»”".contains(String.valueOf(value.charAt(endIndex - 1)))) {
      endIndex--;
    }
    return value.substring(0, endIndex);
  }

  public static String buildMarkdownCaption(final String text) {
    if (!StringUtils.hasText(text)) {
      return null;
    }
    return text.length() <= TELEGRAM_CAPTION_LIMIT ? text : null;
  }

  public static String truncate(final String value, final int maxLength) {
    if (!StringUtils.hasText(value) || maxLength <= 0 || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength).trim();
  }

  public static String extractCommandPayload(final String text) {
    if (!StringUtils.hasText(text)) {
      return "";
    }
    final int spaceIndex = text.indexOf(' ');
    return spaceIndex >= 0 ? text.substring(spaceIndex + 1).trim() : "";
  }

  public static boolean isAddressedToBot(final String text, final String botUsername) {
    if (!StringUtils.hasText(text)) {
      return false;
    }
    final String trimmed = text.trim();
    if (StringUtils.hasText(botUsername)) {
      final String mention = "@" + botUsername;
      if (trimmed.regionMatches(true, 0, mention, 0, mention.length())) {
        return true;
      }
    }
    return startsWithBotPrefix(trimmed);
  }

  public static String prepareNewsPrompt(final String prompt) {
    if (!StringUtils.hasText(prompt)) {
      return prompt;
    }
    final String trimmed = prompt.trim();
    final List<String> urls = extractUrls(trimmed);
    if (urls.isEmpty()) {
      return trimmed;
    }
    final String newsWithoutUrls = removeUrls(trimmed, urls);
    final String basePrompt = StringUtils.hasText(newsWithoutUrls)
        ? newsWithoutUrls
        : "Перепиши новину за інформацією з посилання.";

    final String linkContext = buildLinkContext(urls);
    if (!StringUtils.hasText(linkContext)) {
      return NEWS_LINK_INSTRUCTION
          + "\n\n"
          + newsPrompt()
          + "\n\n"
          + basePrompt
          + "\n\nЯкщо посилання недоступні, використай наявний текст і перепиши новину стандартно.";
    }

    return NEWS_LINK_INSTRUCTION
        + "\n\n"
        + newsPrompt()
        + "\n\n"
        + basePrompt
        + "\n\nІнформація з посилань:\n"
        + linkContext;
  }

  public static String prepareArticlesPrompt(final String prompt) {
    if (!StringUtils.hasText(prompt)) {
      return prompt;
    }
    final String trimmed = prompt.trim();
    final List<String> urls = extractUrls(trimmed);
    if (urls.isEmpty()) {
      return trimmed;
    }
    final String textWithoutUrls = removeUrls(trimmed, urls);
    final String basePrompt = StringUtils.hasText(textWithoutUrls)
        ? textWithoutUrls
        : "Напиши розгорнуту статтю за інформацією з посилання.";

    final String linkContext = buildLinkContext(urls);
    if (!StringUtils.hasText(linkContext)) {
      return ARTICLES_LINK_INSTRUCTION
          + "\n\n"
          + articlesPrompt()
          + "\n\n"
          + basePrompt
          + "\n\nЯкщо посилання недоступні, використай наявний текст і напиши статтю стандартно.";
    }

    return ARTICLES_LINK_INSTRUCTION
        + "\n\n"
        + articlesPrompt()
        + "\n\n"
        + basePrompt
        + "\n\nІнформація з посилань:\n"
        + linkContext;
  }

  public static List<String> extractUrls(final String text) {
    final List<String> urls = new ArrayList<>();
    final Matcher matcher = URL_PATTERN.matcher(text);
    while (matcher.find()) {
      final String url = stripTrailingPunctuation(matcher.group());
      if (StringUtils.hasText(url)) {
        urls.add(url);
      }
    }
    return urls;
  }

  /**
   * Returns true if text contains a URL that looks like TikTok, YouTube or Instagram link.
   */
  public static boolean containsMediaUrl(final String text) {
    if (!StringUtils.hasText(text)) {
      return false;
    }
    final List<String> urls = extractUrls(text);
    for (final String u : urls) {
      final String lower = u.toLowerCase();
      if (lower.contains("tiktok.com") || lower.contains("youtube.com")
          || lower.contains("youtu.be") || lower.contains("instagram.com") || lower.contains("instagr.am")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Extracts the first media URL (tiktok/youtube/instagram) from text or null if none.
   */
  public static String extractFirstUrl(final String text) {
    if (!StringUtils.hasText(text)) {
      return null;
    }
    final List<String> urls = extractUrls(text);
    for (final String u : urls) {
      final String lower = u.toLowerCase();
      if (lower.contains("tiktok.com") || lower.contains("youtube.com")
          || lower.contains("youtu.be") || lower.contains("instagram.com") || lower.contains("instagr.am")) {
        return u;
      }
    }
    return urls.isEmpty() ? null : urls.get(0);
  }

  public static String buildLinkContext(final List<String> urls) {
    final StringBuilder builder = new StringBuilder();
    for (final String url : urls) {
      final String linkContext = fetchLinkContext(url);
      if (!StringUtils.hasText(linkContext)) {
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
          .timeout(Duration.ofSeconds(8))
          .header("User-Agent", "Mozilla/5.0")
          .GET()
          .build();

      final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 200 || response.statusCode() >= 300 || !StringUtils.hasText(response.body())) {
        return null;
      }
      return truncate(extractReadableText(response.body()), 3000);
    } catch (final Exception ex) {
      log.warn("Failed to fetch link context from {}", url, ex);
      return null;
    }
  }

  public static String buildTelegramFileUrl(final String telegramApiBaseUrl, final String botToken, final String filePath) {
    final String baseUrl = telegramApiBaseUrl.endsWith("/")
        ? telegramApiBaseUrl.substring(0, telegramApiBaseUrl.length() - 1)
        : telegramApiBaseUrl;
    return baseUrl + "/file/bot" + botToken + "/" + filePath;
  }

  public static String buildNewsReplyMarkup(
      final boolean hasImage,
      final String copyImageToken,
      final String copyImageCallbackPrefix) {
    try {
      final List<List<Map<String, Object>>> keyboard = new ArrayList<>();
      final List<Map<String, Object>> row = new ArrayList<>();

      if (hasImage && StringUtils.hasText(copyImageToken)) {
        final Map<String, Object> button = Map.of(
            "text", "📷 Отримати фото окремо",
            "callback_data", copyImageCallbackPrefix + copyImageToken
        );
        row.add(button);
      }

      if (!row.isEmpty()) {
        keyboard.add(row);
        final Map<String, Object> inlineKeyboard = Map.of("inline_keyboard", keyboard);
        return OBJECT_MAPPER.writeValueAsString(inlineKeyboard);
      }
    } catch (final Exception ex) {
      log.error("Failed to build news reply markup", ex);
    }
    return null;
  }

  public static String registerCopyImagePayload(final String text, final byte[] photoBytes) {
    final String token = UUID.randomUUID().toString();
    COPY_IMG_PAYLOADS.put(token, new ReplyPayload(text, photoBytes));
    return token;
  }

  public static ReplyPayload getCopyImagePayload(final String token) {
    return COPY_IMG_PAYLOADS.get(token);
  }

  public static void removeCopyImagePayload(final String token) {
    COPY_IMG_PAYLOADS.remove(token);
  }


  public static Object buildUserContentNew(final String prompt, final byte[] mediaBytes, final String mimeType) {
    if (mediaBytes == null || mediaBytes.length == 0) {
      return prompt;
    }

    final String resolvedMimeType = StringUtils.hasText(mimeType) ? mimeType : "image/jpeg";
    final String base64Data = Base64.getEncoder().encodeToString(mediaBytes);

    if (resolvedMimeType.startsWith("audio/")) {
      final String audioFormat = resolvedMimeType.substring("audio/".length());
      return List.of(
          java.util.Map.of("type", "text", "text", prompt),
          java.util.Map.of("type", "input_audio", "input_audio",
              java.util.Map.of("data", base64Data, "format", audioFormat)));
    }

    final String dataUrl = "data:" + resolvedMimeType + ";base64," + base64Data;
    return List.of(
        java.util.Map.of("type", "text", "text", prompt),
        java.util.Map.of("type", "image_url", "image_url", java.util.Map.of("url", dataUrl)));
  }

  public static void addTranscribedInContext(final String message, final String username, final String transcribed,
      final Long chatId, final Map<Long, List<String>> chatContext) {
    log.info("Saved context for {}", username);
    final var json = new JSONObject();
    json.put("userName", message);
    json.put("name", username);
    json.put("text", transcribed);

    addMessageToContext(chatId, json.toJSONString(), chatContext);
  }

  private static void addMessageToContext(final Long chatId, final String jsonMessage, final Map<Long, List<String>> chatContext) {
    final var contextList = chatContext.computeIfAbsent(chatId, k -> new ArrayList<>());
    contextList.add(jsonMessage);

    while (contextList.size() > MAX_CONTEXT_SIZE) {
      contextList.remove(0);
    }
  }

  public static final String HELP = """
      Привіт! Я - твій чат-асистент, який допоможе тобі з різними завданнями. Ось які команди я розумію:
      
      ✨ **Основні команди:**
      
      🔹 **/hello** - Привіт від бота
      
      📰 **/news** - Поділися новиною або цікавою інформацією. Додай фото, якщо хочеш (опційно). Бот обговорить це з тобою!
      
      📄 **/articles** - Створи повноцінну статтю. Поділися посиланням або текстом, і бот згенерує детальну статтю та відправить її у форматі .docx!
      
      📝 **/text** - Транскрибуй аудіо або відео в текст. Пошли мені аудіо/відео файл або відповідь цією командою на аудіо/відеозаписом - я перетворю його на текст.
      
      🎵 **/audio** - Витяги аудіо з відеозаписи в MP3. Пошли мені відеофайл або відповідь цією командою на видеозаписом - я витяну звук.
      
      💬 **/help** - Показати цю інформацію
      
      🚀 **/start** - Почати роботу з ботом
      
      ---
      
      **Як це працює:**
      • Всі твої питання будуть рішені за допомогою Gemini AI
      • У групах бот автоматично транскрибує аудіо та відео
      • Ти можеш поділитися посиланням на медіа - бот завантажить його у фоні
      • Контекст бесід зберігається для кращого розуміння
      
      Готовий допомогти! 😊
      """;

  public record ReplyPayload(String text, byte[] photoBytes) {
  }
}
