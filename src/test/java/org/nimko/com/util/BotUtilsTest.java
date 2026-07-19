package org.nimko.com.util;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BotUtilsTest {

    @Test
    public void testIsGroupChatWithGroupType() {
        Message mockMessage = mock(Message.class);
        Chat mockChat = mock(Chat.class);
        when(mockMessage.getChat()).thenReturn(mockChat);
        when(mockChat.getType()).thenReturn("group");

        assertTrue(BotUtils.isGroupChat(mockMessage));
    }

    @Test
    public void testIsGroupChatWithSuperGroupType() {
        Message mockMessage = mock(Message.class);
        Chat mockChat = mock(Chat.class);
        when(mockMessage.getChat()).thenReturn(mockChat);
        when(mockChat.getType()).thenReturn("supergroup");

        assertTrue(BotUtils.isGroupChat(mockMessage));
    }

    @Test
    public void testIsGroupChatWithPrivateType() {
        Message mockMessage = mock(Message.class);
        Chat mockChat = mock(Chat.class);
        when(mockMessage.getChat()).thenReturn(mockChat);
        when(mockChat.getType()).thenReturn("private");

        assertFalse(BotUtils.isGroupChat(mockMessage));
    }

    @Test
    public void testGetSenderNameWithUsername() {
        User mockUser = mock(User.class);
        when(mockUser.getUserName()).thenReturn("john_doe");
        when(mockUser.getFirstName()).thenReturn("John");

        assertEquals("john_doe", BotUtils.getSenderName(mockUser));
    }

    @Test
    public void testGetSenderNameWithFirstAndLastName() {
        User mockUser = mock(User.class);
        when(mockUser.getUserName()).thenReturn(null);
        when(mockUser.getFirstName()).thenReturn("John");
        when(mockUser.getLastName()).thenReturn("Doe");

        assertEquals("John Doe", BotUtils.getSenderName(mockUser));
    }

    @Test
    public void testGetSenderNameWithFirstNameOnly() {
        User mockUser = mock(User.class);
        when(mockUser.getUserName()).thenReturn("");
        when(mockUser.getFirstName()).thenReturn("John");
        when(mockUser.getLastName()).thenReturn(null);

        assertEquals("John", BotUtils.getSenderName(mockUser));
    }

    @Test
    public void testGetSenderNameWithIdFallback() {
        User mockUser = mock(User.class);
        when(mockUser.getUserName()).thenReturn(null);
        when(mockUser.getFirstName()).thenReturn(null);
        when(mockUser.getId()).thenReturn(12345L);

        assertEquals("12345", BotUtils.getSenderName(mockUser));
    }

    @Test
    public void testIsReplyToBot_NullMessage() {
        assertFalse(BotUtils.isReplyToBot(null, "my_bot"));
    }

    @Test
    public void testIsReplyToBot_NullBotUsername() {
        Message mockMessage = mock(Message.class);
        assertFalse(BotUtils.isReplyToBot(mockMessage, null));
        assertFalse(BotUtils.isReplyToBot(mockMessage, ""));
    }

    @Test
    public void testIsReplyToBot_NoReplyToMessage() {
        Message mockMessage = mock(Message.class);
        when(mockMessage.getReplyToMessage()).thenReturn(null);
        assertFalse(BotUtils.isReplyToBot(mockMessage, "my_bot"));
    }

    @Test
    public void testIsReplyToBot_NoFromUser() {
        Message mockMessage = mock(Message.class);
        Message replyToMessage = mock(Message.class);
        when(mockMessage.getReplyToMessage()).thenReturn(replyToMessage);
        when(replyToMessage.getFrom()).thenReturn(null);
        assertFalse(BotUtils.isReplyToBot(mockMessage, "my_bot"));
    }

    @Test
    public void testIsReplyToBot_DifferentUsername() {
        Message mockMessage = mock(Message.class);
        Message replyToMessage = mock(Message.class);
        User mockUser = mock(User.class);
        when(mockMessage.getReplyToMessage()).thenReturn(replyToMessage);
        when(replyToMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getUserName()).thenReturn("other_user");
        assertFalse(BotUtils.isReplyToBot(mockMessage, "my_bot"));
    }

    @Test
    public void testIsReplyToBot_MatchingUsername() {
        Message mockMessage = mock(Message.class);
        Message replyToMessage = mock(Message.class);
        User mockUser = mock(User.class);
        when(mockMessage.getReplyToMessage()).thenReturn(replyToMessage);
        when(replyToMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getUserName()).thenReturn("my_bot");
        assertTrue(BotUtils.isReplyToBot(mockMessage, "my_bot"));
    }

    @Test
    public void testIsReplyToBot_MatchingUsernameCaseInsensitive() {
        Message mockMessage = mock(Message.class);
        Message replyToMessage = mock(Message.class);
        User mockUser = mock(User.class);
        when(mockMessage.getReplyToMessage()).thenReturn(replyToMessage);
        when(replyToMessage.getFrom()).thenReturn(mockUser);
        when(mockUser.getUserName()).thenReturn("MY_BOT");
        assertTrue(BotUtils.isReplyToBot(mockMessage, "my_bot"));
    }

    @Test
    public void testReadResourceFile_Success() {
        String result = ReadResourceUtils.readResourceFile("prompts/news_prompt.txt");
        assertNotNull(result);
        assertTrue(result.contains("Ти — професійний редактор новин для Telegram."));
    }

    @Test
    public void testReadResourceFile_WithLeadingSlash() {
        String result = ReadResourceUtils.readResourceFile("/prompts/news_prompt.txt");
        assertNotNull(result);
        assertTrue(result.contains("Ти — професійний редактор новин для Telegram."));
    }

    @Test
    public void testReadResourceFile_NotFound() {
        assertThrows(RuntimeException.class, () -> {
           ReadResourceUtils.readResourceFile("prompts/non_existent.txt");
        });
    }

    @Test
    public void testNewsPrompt() {
        String prompt = BotUtils.newsPrompt();
        assertNotNull(prompt);
        assertTrue(prompt.contains("Ти — професійний редактор новин для Telegram."));
    }

    @Test
    public void testArticlesPrompt() {
        String prompt = BotUtils.articlesPrompt();
        assertNotNull(prompt);
        assertTrue(prompt.contains("Ти — висококласний перекладач, редактор та журналіст."));
    }

    @Test
    public void testResolveLocale() {
        assertEquals("uk", BotUtils.resolveLocale(null).getLanguage());
        assertEquals("uk", BotUtils.resolveLocale("uk").getLanguage());
        assertEquals("uk", BotUtils.resolveLocale("ua").getLanguage());
        assertEquals("ru", BotUtils.resolveLocale("ru").getLanguage());
        assertEquals("en", BotUtils.resolveLocale("en").getLanguage());
        assertEquals("sk", BotUtils.resolveLocale("sk").getLanguage());
        assertEquals("uk", BotUtils.resolveLocale("fr").getLanguage());
    }

    @Test
    public void testDetectGroupLanguage_Ukrainian() {
        String result = BotUtils.detectGroupLanguage("Привіт, як справи? У мене все добре і чудово.", null);
        assertEquals("uk", result);
    }

    @Test
    public void testDetectGroupLanguage_Russian() {
        String result = BotUtils.detectGroupLanguage("Привет, как дела? У меня всё отлично, этот день хороший.", null);
        assertEquals("ru", result);
    }

    @Test
    public void testDetectGroupLanguage_HistoryFallback() {
        java.util.List<String> history = java.util.List.of(
            "{\"text\":\"Привет, как дела? У меня все хорошо.\"}",
            "{\"text\":\"Тут русские буквы\"}"
        );
        String result = BotUtils.detectGroupLanguage("/help", history);
        assertEquals("ru", result);
    }

    @Test
    public void testDetectGroupLanguage_NoMatch() {
        String result = BotUtils.detectGroupLanguage("Hello, how are you?", null);
        assertNull(result);
    }
}
