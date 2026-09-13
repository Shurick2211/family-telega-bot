package org.nimko.com.util;

import org.nimko.com.repository.ChatContextRepository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BotUtilsTest {

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
