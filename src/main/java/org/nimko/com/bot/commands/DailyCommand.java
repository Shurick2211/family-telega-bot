package org.nimko.com.bot.commands;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.nimko.com.bot.FamilyTelegramBot.ReplyData;
import org.nimko.com.entity.DaylySummaryChatEntity;
import org.nimko.com.repository.DailySummaryChatRepository;
import org.nimko.com.services.I18nService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Service
@RequiredArgsConstructor
@Order(1)
public class DailyCommand implements CommandProcess {

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

  private final I18nService i18nService;
  private final DailySummaryChatRepository dailySummaryChatRepository;

  @Override
  public boolean isCommand(final String command) {
    return "/daily".equals(command);
  }

  @Override
  public ReplyData execute(final String normalizedText, final boolean hasPhoto, final byte[] imageBytes,
      final Message message, final Long chatId, final boolean hasVoice, final byte[] rawAudioBytes,
      final byte[] extractedAudioFromVideoBytes, final boolean groupChat, final int messageId) {
    final String dateArg = StringUtils.substringAfter(normalizedText, " ").trim();
    if (StringUtils.isBlank(dateArg)) {
      return new ReplyData(i18nService.getTranslate("bot.daily.usage"), false);
    }

    final LocalDate date;
    try {
      date = LocalDate.parse(dateArg, DATE_FORMATTER);
    } catch (final DateTimeParseException ex) {
      return new ReplyData(i18nService.getTranslate("bot.daily.invalidDate"), false);
    }

    final Instant startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
    final Instant endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

    final List<DaylySummaryChatEntity> summaries = dailySummaryChatRepository
        .findByChatIdAndCreatedAtBetweenOrderByIdAsc(chatId, startOfDay, endOfDay);

    if (summaries.isEmpty()) {
      return new ReplyData(i18nService.getTranslate("bot.daily.empty", dateArg), false);
    }

    final String text = summaries.stream()
        .map(DaylySummaryChatEntity::getText)
        .collect(Collectors.joining("\n\n"));

    return new ReplyData(text, false);
  }
}
