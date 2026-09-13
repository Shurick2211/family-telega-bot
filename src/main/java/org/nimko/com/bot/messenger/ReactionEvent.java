package org.nimko.com.bot.messenger;

import java.util.List;

public interface ReactionEvent {

  Long getChatId();

  Integer getMessageId();

  MessengerUser getUser();

  boolean isGroupChat();

  List<String> getReactionValues();

}
