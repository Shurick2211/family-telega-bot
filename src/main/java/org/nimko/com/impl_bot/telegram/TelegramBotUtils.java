package org.nimko.com.impl_bot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionType;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeCustomEmoji;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeEmoji;

@UtilityClass
public class TelegramBotUtils {

  private static final Logger log = LoggerFactory.getLogger(TelegramBotUtils.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final int MAX_COPY_IMG_PAYLOADS = 200;

  private static final Map<String, ReplyPayload> COPY_IMG_PAYLOADS =
      Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(
            final Map.Entry<String, ReplyPayload> eldest) {
          return size() > MAX_COPY_IMG_PAYLOADS;
        }
      });

  public static final int TELEGRAM_CAPTION_LIMIT = 1024;

  public static String resolveIncomingText(final Message message) {
    if (message == null) {
      return null;
    }
    if (message.hasText()) {
      return message.getText();
    }
    if (StringUtils.isNotBlank(message.getCaption())) {
      return message.getCaption();
    }
    return null;
  }

  public static boolean isGroupChat(final Message message) {
    if (message == null || message.getChat() == null) {
      return false;
    }
    final String type = message.getChat().getType();
    return groupOrSuperSuperGroup(message.getChat(), type);
  }

  public static boolean isGroupChat(final Chat chat) {
    if (chat == null) {
      return false;
    }
    final String type = chat.getType();
    return groupOrSuperSuperGroup(chat, type);
  }

  private static boolean groupOrSuperSuperGroup(final Chat chat, final String type) {
    return "group".equalsIgnoreCase(type) || "supergroup".equalsIgnoreCase(type)
        || Boolean.TRUE.equals(chat.isGroupChat())
        || Boolean.TRUE.equals(chat.isSuperGroupChat());
  }

  public static String getReactionString(final ReactionType reaction) {
    if (reaction == null) {
      return "";
    }
    if (reaction instanceof final ReactionTypeEmoji emojiReaction) {
      return emojiReaction.getEmoji();
    }
    if (reaction instanceof final ReactionTypeCustomEmoji customEmojiReaction) {
      return customEmojiReaction.getCustomEmojiId();
    }
    return reaction.getType();
  }

  public static String getSenderPersonName(final User user) {
    if (user == null) {
      return "Unknown";
    }
    final String firstName = user.getFirstName();
    if (StringUtils.isNotBlank(firstName)) {
      final String lastName = user.getLastName();
      return StringUtils.isNotBlank(lastName) ? firstName + " " + lastName : firstName;
    }
    if (StringUtils.isNotBlank(user.getUserName())) {
      return user.getUserName();
    }
    return user.getId().toString();
  }

  public static String getSenderName(final User user) {
    if (user == null) {
      return "Unknown";
    }
    if (StringUtils.isNotBlank(user.getUserName())) {
      return user.getUserName();
    }
    return user.getId().toString();
  }

  public static boolean isReplyToBot(final Message message, final String botUsername) {
    if (message == null || StringUtils.isBlank(botUsername)) {
      return false;
    }
    final Message replyTo = message.getReplyToMessage();
    if (replyTo == null) {
      return false;
    }
    final User from = replyTo.getFrom();
    if (from == null) {
      return false;
    }
    return botUsername.equalsIgnoreCase(from.getUserName());
  }

  public static String buildTelegramFileUrl(final String telegramApiBaseUrl, final String botToken,
      final String filePath) {
    final String baseUrl = telegramApiBaseUrl.endsWith("/")
        ? telegramApiBaseUrl.substring(0, telegramApiBaseUrl.length() - 1)
        : telegramApiBaseUrl;
    return baseUrl + "/file/bot" + botToken + "/" + filePath;
  }

  public static String buildMarkdownCaption(final String text) {
    if (StringUtils.isBlank(text)) {
      return null;
    }
    return text.length() <= TELEGRAM_CAPTION_LIMIT ? text : null;
  }

  public static String buildNewsReplyMarkup(
      final boolean hasImage,
      final String copyImageToken,
      final String copyImageCallbackPrefix) {
    try {
      final List<List<Map<String, Object>>> keyboard = new ArrayList<>();
      final List<Map<String, Object>> row = new ArrayList<>();

      if (hasImage && StringUtils.isNotBlank(copyImageToken)) {
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

  public record ReplyPayload(String text, byte[] photoBytes) {

  }

}
