package org.nimko.com.impl_bot.telegram.messanger;

import org.nimko.com.bot.messenger.MessengerUser;
import org.nimko.com.impl_bot.telegram.TelegramBotUtils;
import org.telegram.telegrambots.meta.api.objects.User;

public class TelegramMessengerUser implements MessengerUser {

  private final User raw;

  public TelegramMessengerUser(final User raw) {
    this.raw = raw;
  }

  public User raw() {
    return raw;
  }

  @Override
  public Long getId() {
    return raw.getId();
  }

  @Override
  public String getUsername() {
    return TelegramBotUtils.getSenderName(raw);
  }

  @Override
  public String getPersonName() {
    return TelegramBotUtils.getSenderPersonName(raw);
  }

  @Override
  public String getLanguageCode() {
    return raw.getLanguageCode();
  }

}
