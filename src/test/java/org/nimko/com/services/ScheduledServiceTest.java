package org.nimko.com.services;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.BotProperties;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.repository.ChatContextRepository;
import org.nimko.com.repository.DailySummaryChatRepository;

public class ScheduledServiceTest {

  @Test
  public void testClearChatContext() {
    ChatContextRepository chatContextRepository = mock(ChatContextRepository.class);
    DailySummaryChatRepository dailySummaryChatRepository = mock(DailySummaryChatRepository.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    AiChatService aiChatService = mock(AiChatService.class);
    BotSenderService botSenderService = mock(BotSenderService.class);
    BotProperties botProperties = mock(BotProperties.class);

    TermuxService termuxService = mock(TermuxService.class);
    ScheduledService service = new ScheduledService(
        chatContextRepository,
        dailySummaryChatRepository,
        objectMapper,
        aiChatService,
        botSenderService,
        botProperties,
        ZoneId.of("Europe/Kyiv"),
        termuxService
    );

    service.clearChatContext();

    verify(chatContextRepository, times(1)).deleteByCreatedAtBefore(any(Instant.class));
    verify(chatContextRepository, times(1)).deleteByGroupChatFalseAndCreatedAtBefore(any(Instant.class));
  }

  @Test
  public void testSendDailySummaryNoChats() {
    ChatContextRepository chatContextRepository = mock(ChatContextRepository.class);
    DailySummaryChatRepository dailySummaryChatRepository = mock(DailySummaryChatRepository.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    AiChatService aiChatService = mock(AiChatService.class);
    BotSenderService botSenderService = mock(BotSenderService.class);
    BotProperties botProperties = mock(BotProperties.class);

    when(chatContextRepository.findDistinctChatIdByCreatedAtBetweenAndGroupChatTrue(any(Instant.class), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    TermuxService termuxService = mock(TermuxService.class);
    ScheduledService service = new ScheduledService(
        chatContextRepository,
        dailySummaryChatRepository,
        objectMapper,
        aiChatService,
        botSenderService,
        botProperties,
        ZoneId.of("Europe/Kyiv"),
        termuxService
    );

    service.sendDailySummary();

    verify(chatContextRepository, times(1))
        .findDistinctChatIdByCreatedAtBetweenAndGroupChatTrue(any(Instant.class), any(Instant.class));
    verifyNoInteractions(aiChatService);
    verifyNoInteractions(botSenderService);
  }
}
