package org.nimko.com.services;

import static org.nimko.com.repository.ChatContextRepository.getTodayContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.ai.AiChatService;
import org.nimko.com.bot.BotSenderService;
import org.nimko.com.config.TelegramBotProperties;
import org.nimko.com.entity.DaylySummaryChatEntity;
import org.nimko.com.repository.ChatContextRepository;
import org.nimko.com.repository.DailySummaryChatRepository;
import org.nimko.com.util.BotUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledService {

  public static final int TIME_AVAILABLE_HISTORY_CONTEXT = 30;
  private static final String DAILY_SUMMARY_PROMPT = "Подведи юмористические (шуточные) итоги дня";
  private static final String MONTHLY_SUMMARY_PROMPT = "Подведи юмористические (шуточные) итоги месяца";
  private static final LocalTime LOW_BATTERY_ALERT_START = LocalTime.of(8, 0);
  private static final LocalTime LOW_BATTERY_ALERT_END = LocalTime.of(21, 0);
  private static final int LOW_BATTERY_THRESHOLD_PERCENT = 20;
  private static final int LOW_BATTERY_FLASHLIGHT_BLINKS = 3;
  private static final long LOW_BATTERY_FLASHLIGHT_INTERVAL_MS = 300;
  private static final String CHARGE_ME_MESSAGE = "Поставь меня на зарядку!";

  private final ChatContextRepository chatContextRepository;
  private final DailySummaryChatRepository dailySummaryChatRepository;
  private final ObjectMapper objectMapper;
  private final AiChatService aiChatService;
  private final BotSenderService botSenderService;
  private final TelegramBotProperties telegramBotProperties;
  private final ZoneId appZoneId;
  private final TermuxService termuxService;


  @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
  public void heartbeat() {
    final var now = ZonedDateTime.now(appZoneId);
    final var batteryInfo = termuxService.getBatteryStatus();
    log.info("Scheduler heartbeat: now={} zone={} (jvm default zone={}) batteryInfo={}",
        now , appZoneId, ZoneId.systemDefault(), batteryInfo);

    final LocalTime currentTime = now.toLocalTime();
    if (!currentTime.isBefore(LOW_BATTERY_ALERT_START) && !currentTime.isAfter(LOW_BATTERY_ALERT_END)
        && batteryInfo.percentage() <= LOW_BATTERY_THRESHOLD_PERCENT) {
      log.info("Low battery detected: {}%, alerting", batteryInfo.percentage());
      termuxService.blinkFlashlight(LOW_BATTERY_FLASHLIGHT_BLINKS, LOW_BATTERY_FLASHLIGHT_INTERVAL_MS);
      termuxService.speak(CHARGE_ME_MESSAGE);
      botSenderService.sendTextReply(telegramBotProperties.newsChatId(), CHARGE_ME_MESSAGE);
    }
  }

  @Transactional
  @Scheduled(cron = "${app.schedule.clear-context-cron}", zone = "${app.timezone}")
  public void clearChatContext() {
    log.info("Running scheduled task clearChatContext");
    final Instant monthAgo = Instant.now().minus(TIME_AVAILABLE_HISTORY_CONTEXT, ChronoUnit.DAYS);
    chatContextRepository.deleteByCreatedAtBefore(monthAgo);
  }

  @Scheduled(cron = "${app.schedule.daily-summary-cron}", zone = "${app.timezone}")
  public void sendDailySummary() {
    log.info("Running scheduled task sendDailySummary");
    final LocalDate today = LocalDate.now(appZoneId);
    final Instant startOfDay = today.atStartOfDay(appZoneId).toInstant();
    final Instant endOfDay = today.plusDays(1).atStartOfDay(appZoneId).toInstant();

    final List<Long> chatIds = chatContextRepository.findDistinctChatIdByCreatedAtBetweenAndGroupChatTrue(
        startOfDay, endOfDay);
    log.info("Found {} chats for daily summary: {}", chatIds.size(), chatIds);

    for (final Long chatId : chatIds) {
      final List<String> context = getTodayContext(chatContextRepository, objectMapper, chatId);
      if (context == null || context.isEmpty()) {
        log.info("Chat {} has no context, skipping daily summary", chatId);
        continue;
      }

      final String prompt = BotUtils.stripBotPrefix(DAILY_SUMMARY_PROMPT, telegramBotProperties.username(), context,
          true);
      final String summary;
      try {
        summary = aiChatService.ask(prompt);
      } catch (final Exception ex) {
        log.error("Failed to generate daily summary for chat {}", chatId, ex);
        continue;
      }
      if (StringUtils.isNotBlank(summary)) {
        botSenderService.sendReply(chatId, summary, null);
        dailySummaryChatRepository.save(new DaylySummaryChatEntity()
            .setChatId(chatId).setText(summary));
        log.info("Sent daily summary to chat {}", chatId);
      } else {
        log.info("Empty summary from AI for chat {}, nothing sent", chatId);
      }
    }
  }

  @Scheduled(cron = "${app.schedule.monthly-summary-cron}", zone = "${app.timezone}")
  public void sendMonthlySummary() {
    log.info("Running scheduled task sendMonthlySummary (checking last day of month)");
    final LocalDate today = LocalDate.now(appZoneId);
    if (!today.equals(today.withDayOfMonth(today.lengthOfMonth()))) {
      log.info("Today is not the last day of the month, skipping monthly summary");
      return;
    }
    log.info("Today is the last day of the month, sending monthly summary");

    final Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(appZoneId).toInstant();
    final Instant endOfMonth = today.plusDays(1).atStartOfDay(appZoneId).toInstant();

    final List<Long> chatIds = dailySummaryChatRepository.findDistinctChatIdByCreatedAtBetween(
        startOfMonth, endOfMonth);

    for (final Long chatId : chatIds) {
      final List<String> context = dailySummaryChatRepository
          .findByChatIdAndCreatedAtBetweenOrderByIdAsc(chatId, startOfMonth, endOfMonth).stream()
          .map(DaylySummaryChatEntity::getText)
          .toList();
      if (context.isEmpty()) {
        continue;
      }

      final String prompt = BotUtils.stripBotPrefix(MONTHLY_SUMMARY_PROMPT, telegramBotProperties.username(), context,
          true);
      final String summary = aiChatService.ask(prompt);
      if (StringUtils.isNotBlank(summary)) {
        botSenderService.sendReply(chatId, summary, null);
      }
    }
  }
}
