package org.nimko.com.util;

import org.nimko.com.repository.ChatContextRepository;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionType;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeEmoji;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypeCustomEmoji;
import org.telegram.telegrambots.meta.api.objects.reactions.ReactionTypePaid;

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

    @Test
    public void testIsGroupChatWithChat_Group() {
        Chat mockChat = mock(Chat.class);
        when(mockChat.getType()).thenReturn("group");
        assertTrue(BotUtils.isGroupChat(mockChat));
    }

    @Test
    public void testIsGroupChatWithChat_SuperGroup() {
        Chat mockChat = mock(Chat.class);
        when(mockChat.getType()).thenReturn("supergroup");
        assertTrue(BotUtils.isGroupChat(mockChat));
    }

    @Test
    public void testIsGroupChatWithChat_Private() {
        Chat mockChat = mock(Chat.class);
        when(mockChat.getType()).thenReturn("private");
        assertFalse(BotUtils.isGroupChat(mockChat));
    }

    @Test
    public void testGetReactionString_Null() {
        assertEquals("", BotUtils.getReactionString(null));
    }

    @Test
    public void testGetReactionString_Emoji() {
        ReactionTypeEmoji mockEmoji = mock(ReactionTypeEmoji.class);
        when(mockEmoji.getEmoji()).thenReturn("👍");
        assertEquals("👍", BotUtils.getReactionString(mockEmoji));
    }

    @Test
    public void testGetReactionString_CustomEmoji() {
        ReactionTypeCustomEmoji mockCustom = mock(ReactionTypeCustomEmoji.class);
        when(mockCustom.getCustomEmojiId()).thenReturn("custom_123");
        assertEquals("custom_123", BotUtils.getReactionString(mockCustom));
    }

    @Test
    public void testGetReactionString_Paid() {
        ReactionTypePaid mockPaid = mock(ReactionTypePaid.class);
        when(mockPaid.getType()).thenReturn("paid");
        assertEquals("paid", BotUtils.getReactionString(mockPaid));
    }

    @Test
    public void testNormalizeCommandWithNewlineSeparator() {
        assertEquals("/doc", BotUtils.normalizeCommand("/doc\nзроби звіт"));
        assertEquals("/doc", BotUtils.normalizeCommand("/doc@family_bot\nзроби звіт"));
        assertEquals("/doc", BotUtils.normalizeCommand("/doc зроби звіт"));
        assertEquals("/doc", BotUtils.normalizeCommand("/doc"));
    }

    @Test
    public void testExtractCommandPayloadWithNewlineSeparator() {
        assertEquals("зроби звіт", BotUtils.extractCommandPayload("/doc\nзроби звіт"));
        assertEquals("рядок 1\nрядок 2",
                BotUtils.extractCommandPayload("/doc\nрядок 1\nрядок 2"));
    }

    @Test
    public void testCopyImagePayloadLifecycle() {
        byte[] bytes = new byte[]{1, 2, 3};
        String token = BotUtils.registerCopyImagePayload("test", bytes);
        assertNotNull(token);

        BotUtils.ReplyPayload payload = BotUtils.getCopyImagePayload(token);
        assertNotNull(payload);
        assertEquals("test", payload.text());
        assertArrayEquals(bytes, payload.photoBytes());

        BotUtils.removeCopyImagePayload(token);
        assertNull(BotUtils.getCopyImagePayload(token));
    }

    @Test
    public void testAddTranscribedInContext_PersonalChat() {
        ChatContextRepository mockRepo = mock(ChatContextRepository.class);
        
        BotUtils.addTranscribedInContext(
            "user", "User", "Hello", 111L, 12, mockRepo, false
        );
        
        verify(mockRepo, times(1)).save(any());
    }

    @Test
    public void testAddTranscribedInContext_GroupChat() {
        ChatContextRepository mockRepo = mock(ChatContextRepository.class);
        
        BotUtils.addTranscribedInContext(
            "user", "User", "Hello", 111L, 12, mockRepo, true
        );
        
        verify(mockRepo, times(1)).save(any());
    }

    @Test
    public void testGetTodayContext_PersonalChat_FiltersToLastThreeHours() throws Exception {
        ChatContextRepository mockRepo = mock(ChatContextRepository.class);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        
        java.time.Instant now = java.time.Instant.now();
        org.nimko.com.entity.ChatContextEntity oldEntity = new org.nimko.com.entity.ChatContextEntity()
            .setChatId(222L)
            .setUserName("user")
            .setMessage("old")
            .setGroupChat(false)
            .setCreatedAt(now.minus(4, java.time.temporal.ChronoUnit.HOURS));
            
        org.nimko.com.entity.ChatContextEntity newEntity = new org.nimko.com.entity.ChatContextEntity()
            .setChatId(222L)
            .setUserName("user")
            .setMessage("new")
            .setGroupChat(false)
            .setCreatedAt(now.minus(1, java.time.temporal.ChronoUnit.HOURS));
            
        when(mockRepo.findByChatIdAndCreatedAtBetweenOrderByIdAsc(eq(222L), any(), any()))
            .thenReturn(java.util.List.of(oldEntity, newEntity));
            
        java.util.List<String> results = ChatContextRepository.getTodayContext(mockRepo, mapper, 222L, 999L);
        
        assertEquals(1, results.size());
        assertTrue(results.get(0).contains("new"));
    }

    @Test
    public void testGetTodayContext_NewsChat_DoesNotFilter() throws Exception {
        ChatContextRepository mockRepo = mock(ChatContextRepository.class);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        
        java.time.Instant now = java.time.Instant.now();
        org.nimko.com.entity.ChatContextEntity oldEntity = new org.nimko.com.entity.ChatContextEntity()
            .setChatId(999L)
            .setUserName("user")
            .setMessage("old")
            .setGroupChat(false)
            .setCreatedAt(now.minus(4, java.time.temporal.ChronoUnit.HOURS));
            
        when(mockRepo.findByChatIdAndCreatedAtBetweenOrderByIdAsc(eq(999L), any(), any()))
            .thenReturn(java.util.List.of(oldEntity));
            
        java.util.List<String> results = ChatContextRepository.getTodayContext(mockRepo, mapper, 999L, 999L);
        
        assertEquals(1, results.size());
        assertTrue(results.get(0).contains("old"));
    }
}
