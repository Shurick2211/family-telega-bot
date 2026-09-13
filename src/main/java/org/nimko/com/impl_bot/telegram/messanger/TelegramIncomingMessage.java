package org.nimko.com.impl_bot.telegram.messanger;

import org.nimko.com.bot.messenger.IncomingMessage;
import org.nimko.com.bot.messenger.MessengerUser;
import org.nimko.com.impl_bot.telegram.TelegramBotUtils;
import org.telegram.telegrambots.meta.api.objects.message.Message;

public class TelegramIncomingMessage implements IncomingMessage {

  private final Message raw;

  public TelegramIncomingMessage(final Message raw) {
    this.raw = raw;
  }

  public Message raw() {
    return raw;
  }

  @Override
  public Long getChatId() {
    return raw.getChatId();
  }

  @Override
  public Integer getMessageId() {
    return raw.getMessageId();
  }

  @Override
  public String getText() {
    return TelegramBotUtils.resolveIncomingText(raw);
  }

  @Override
  public boolean hasPhoto() {
    return raw.hasPhoto();
  }

  @Override
  public boolean hasVoice() {
    return raw.hasVoice();
  }

  @Override
  public boolean hasAudio() {
    return raw.hasAudio();
  }

  @Override
  public boolean hasVideoNote() {
    return raw.hasVideoNote();
  }

  @Override
  public boolean hasVideo() {
    return raw.hasVideo();
  }

  @Override
  public boolean hasDocument() {
    return raw.hasDocument();
  }

  @Override
  public String getDocumentFileName() {
    return raw.hasDocument() && raw.getDocument() != null ? raw.getDocument().getFileName() : null;
  }

  @Override
  public boolean isGroupChat() {
    return TelegramBotUtils.isGroupChat(raw);
  }

  @Override
  public MessengerUser getFrom() {
    return raw.getFrom() != null ? new TelegramMessengerUser(raw.getFrom()) : null;
  }

  @Override
  public IncomingMessage getReplyToMessage() {
    return raw.getReplyToMessage() != null ? new TelegramIncomingMessage(raw.getReplyToMessage())
        : null;
  }

  @Override
  public boolean isReplyToBot(final String botUsername) {
    return TelegramBotUtils.isReplyToBot(raw, botUsername);
  }

}
