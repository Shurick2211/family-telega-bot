package org.nimko.com.impl_bot.telegram;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeCustomEmoji;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeEmoji;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypePaid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TelegramBotUtilsTest {

    @Test
    public void testIsGroupChatWithGroupType() {
        Message mockMessage = mock(Message.class);
        Chat mockChat = mock(Chat.class);
        when(mockMessage.getChat()).thenReturn(mockChat);
        when(mockChat.getType()).thenReturn("group");

        assertTrue(TelegramBotUtils.isGroupChat(mockMessage));
    }

    @Test
    public void testIsGroupChatWithSuperGroupType() {
        Message mockMessage = mock(Message.class);
        Chat mockChat = mock(Chat.class);
        when(mockMessage.getChat()).thenReturn(mockChat);
        when(mockChat.getType()).thenReturn("supergroup");

        assertTrue(TelegramBotUtils.isGroupChat(mockMessage));
    }

    @Test
    public void testIsGroupChatWithPrivateType() {
        Message mockMessage = mock(Message.class);
        Chat mockChat = mock(Chat.class);
        when(mockMessage.getChat()).thenReturn(mockChat);
        when(mockChat.getType()).thenReturn("private");

        assertFalse(TelegramBotUtils.isGroupChat(mockMessage));
    }

    @Test
    public void testGetSenderNameWithUsername() {
        User mockUser = mock(User.class);
        when(mockUser.getUserName()).thenReturn("john_doe");
        when(mockUser.getFirstName()).thenReturn("John");

        assertEquals("john_doe", TelegramBotUtils.getSenderName(mockUser));
    }

    @Test
    public void testGetSenderNameWithFirstAndLastName() {
        User mockUser = mock(User.class);
        when(mockUser.getUserName()).thenReturn(null);
        when(mockUser.getFirstName()).thenReturn("John");
        when(mockUser.getLastName()).thenReturn("Doe");

        assertEquals("John Doe", TelegramBotUtils.getSenderPersonName(mockUser));
    }

    @Test
    public void testGetSenderNameWithFirstNameOnly() {
        User mockUser = mock(User.class);
        when(mockUser.getUserName()).thenReturn("");
        when(mockUser.getFirstName()).thenReturn("John");
        when(mockUser.getLastName()).thenReturn(null);

        assertEquals("John", TelegramBotUtils.getSenderPersonName(mockUser));
    }

    @Test
    public void testGetSenderNameWithIdFallback() {
        User mockUser = mock(User.class);
        when(mockUser.getUserName()).thenReturn(null);
        when(mockUser.getFirstName()).thenReturn(null);
        when(mockUser.getId()).thenReturn(12345L);

        assertEquals("12345", TelegramBotUtils.getSenderName(mockUser));
    }

    @Test
    public void testIsReplyToBot_NullMessage() {
        assertFalse(TelegramBotUtils.isReplyToBot(null, "my_bot"));
    }

    @Test
    public void testIsReplyToBot_NullBotUsername() {
        Message mockMessage = mock(Message.class);
        assertFalse(TelegramBotUtils.isReplyToBot(mockMessage, null));
        assertFalse(TelegramBotUtils.isReplyToBot(mockMessage, ""));
    }

    @Test
    public void testIsReplyToBot_NoReplyToMessage() {
        Message mockMessage = mock(Message.class);
        when(mockMessage.getReplyToMessage()).thenReturn(null);
        assertFalse(TelegramBotUtils.isReplyToBot(mockMessage, "my_bot"));
    }

    @Test
    public void testIsReplyToBot_NoFromUser() {
        Message mockMessage = mock(Message.class);
        Message replyToMessage = mock(Message.class);
        when(mockMessage.getReplyToMessage()).thenReturn(replyToMessage);
        when(replyToMessage.getFrom()).thenReturn(null);
        assertFalse(TelegramBotUtils.isReplyToBot(mockMessage, "my_bot"));
    }

    @Test
    public void testIsReplyToBot_DifferentUsername() {
        Message mockMessage = mock(Message.class);
        Message replyToMessage = mock(Message.class);
        User mockUser = mock(User.class);
        when(mockMessage.getReplyToMessage()).thenReturn(replyToMessage);
        when(replyToMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getUserName()).thenReturn("other_user");
        assertFalse(TelegramBotUtils.isReplyToBot(mockMessage, "my_bot"));
    }

    @Test
    public void testIsReplyToBot_MatchingUsername() {
        Message mockMessage = mock(Message.class);
        Message replyToMessage = mock(Message.class);
        User mockUser = mock(User.class);
        when(mockMessage.getReplyToMessage()).thenReturn(replyToMessage);
        when(replyToMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getUserName()).thenReturn("my_bot");
        assertTrue(TelegramBotUtils.isReplyToBot(mockMessage, "my_bot"));
    }

    @Test
    public void testIsReplyToBot_MatchingUsernameCaseInsensitive() {
        Message mockMessage = mock(Message.class);
        Message replyToMessage = mock(Message.class);
        User mockUser = mock(User.class);
        when(mockMessage.getReplyToMessage()).thenReturn(replyToMessage);
        when(replyToMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getUserName()).thenReturn("MY_BOT");
        assertTrue(TelegramBotUtils.isReplyToBot(mockMessage, "my_bot"));
    }

    @Test
    public void testIsGroupChatWithChat_Group() {
        Chat mockChat = mock(Chat.class);
        when(mockChat.getType()).thenReturn("group");
        assertTrue(TelegramBotUtils.isGroupChat(mockChat));
    }

    @Test
    public void testIsGroupChatWithChat_SuperGroup() {
        Chat mockChat = mock(Chat.class);
        when(mockChat.getType()).thenReturn("supergroup");
        assertTrue(TelegramBotUtils.isGroupChat(mockChat));
    }

    @Test
    public void testIsGroupChatWithChat_Private() {
        Chat mockChat = mock(Chat.class);
        when(mockChat.getType()).thenReturn("private");
        assertFalse(TelegramBotUtils.isGroupChat(mockChat));
    }

    @Test
    public void testGetReactionString_Null() {
        assertEquals("", TelegramBotUtils.getReactionString(null));
    }

    @Test
    public void testGetReactionString_Emoji() {
        ReactionTypeEmoji mockEmoji = mock(ReactionTypeEmoji.class);
        when(mockEmoji.getEmoji()).thenReturn("👍");
        assertEquals("👍", TelegramBotUtils.getReactionString(mockEmoji));
    }

    @Test
    public void testGetReactionString_CustomEmoji() {
        ReactionTypeCustomEmoji mockCustom = mock(ReactionTypeCustomEmoji.class);
        when(mockCustom.getCustomEmojiId()).thenReturn("custom_123");
        assertEquals("custom_123", TelegramBotUtils.getReactionString(mockCustom));
    }

    @Test
    public void testGetReactionString_Paid() {
        ReactionTypePaid mockPaid = mock(ReactionTypePaid.class);
        when(mockPaid.getType()).thenReturn("paid");
        assertEquals("paid", TelegramBotUtils.getReactionString(mockPaid));
    }

    @Test
    public void testCopyImagePayloadLifecycle() {
        byte[] bytes = new byte[]{1, 2, 3};
        String token = TelegramBotUtils.registerCopyImagePayload("test", bytes);
        assertNotNull(token);

        TelegramBotUtils.ReplyPayload payload = TelegramBotUtils.getCopyImagePayload(token);
        assertNotNull(payload);
        assertEquals("test", payload.text());
        assertArrayEquals(bytes, payload.photoBytes());

        TelegramBotUtils.removeCopyImagePayload(token);
        assertNull(TelegramBotUtils.getCopyImagePayload(token));
    }
}
