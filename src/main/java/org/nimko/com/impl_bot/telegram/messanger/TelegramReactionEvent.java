package org.nimko.com.impl_bot.telegram.messanger;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.bot.messenger.MessengerUser;
import org.nimko.com.bot.messenger.ReactionEvent;
import org.nimko.com.impl_bot.telegram.TelegramBotUtils;
import org.telegram.telegrambots.meta.api.objects.reactions.MessageReactionUpdated;

public class TelegramReactionEvent implements ReactionEvent {

  private final MessageReactionUpdated raw;

  public TelegramReactionEvent(final MessageReactionUpdated raw) {
    this.raw = raw;
  }

  @Override
  public Long getChatId() {
    return raw.getChat() != null ? raw.getChat().getId() : null;
  }

  @Override
  public Integer getMessageId() {
    return raw.getMessageId();
  }

  @Override
  public MessengerUser getUser() {
    return raw.getUser() != null ? new TelegramMessengerUser(raw.getUser()) : null;
  }

  @Override
  public boolean isGroupChat() {
    return TelegramBotUtils.isGroupChat(raw.getChat());
  }

  @Override
  public List<String> getReactionValues() {
    if (raw.getNewReaction() == null) {
      return List.of();
    }
    return raw.getNewReaction().stream()
        .map(TelegramBotUtils::getReactionString)
        .filter(StringUtils::isNotBlank)
        .toList();
  }

}
