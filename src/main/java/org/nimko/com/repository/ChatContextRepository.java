package org.nimko.com.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.nimko.com.entity.ChatContextEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ChatContextRepository extends JpaRepository<ChatContextEntity, Long> {

  Optional<ChatContextEntity> findByChatIdAndMessageId(Long chatId, int messageId);

  List<ChatContextEntity> findByChatIdAndCreatedAtBetweenOrderByIdAsc(Long chatId,
      Instant createdAtFrom, Instant createdAtTo);

  void deleteByCreatedAtBefore(Instant createdAtBefore);

  @Modifying
  @Transactional
  void deleteByGroupChatFalseAndCreatedAtBefore(Instant createdAtBefore);

  @Query("SELECT DISTINCT c.chatId FROM ChatContextEntity c WHERE c.createdAt BETWEEN :createdAtFrom AND :createdAtTo AND c.groupChat = true")
  List<Long> findDistinctChatIdByCreatedAtBetweenAndGroupChatTrue(
      @Param("createdAtFrom") Instant createdAtFrom,
      @Param("createdAtTo") Instant createdAtTo);

  static List<String> getTodayContext(final ChatContextRepository chatContextRepository,
      final ObjectMapper objectMapper, final Long chatId) {
    return getTodayContext(chatContextRepository, objectMapper, chatId, null, ZoneId.of("Europe/Kyiv"));
  }

  static List<String> getTodayContext(final ChatContextRepository chatContextRepository,
      final ObjectMapper objectMapper, final Long chatId, final Long newsChatId) {
    return getTodayContext(chatContextRepository, objectMapper, chatId, newsChatId, ZoneId.of("Europe/Kyiv"));
  }

  static List<String> getTodayContext(final ChatContextRepository chatContextRepository,
      final ObjectMapper objectMapper, final Long chatId, final Long newsChatId, final ZoneId zoneId) {
    final ZoneId resolvedZone = zoneId != null ? zoneId : ZoneId.of("Europe/Kyiv");
    final LocalDate today = LocalDate.now(resolvedZone);
    final Instant startOfDay = today.atStartOfDay(resolvedZone).toInstant();
    final Instant endOfDay = today.plusDays(1).atStartOfDay(resolvedZone).toInstant();
    List<ChatContextEntity> entities = chatContextRepository
        .findByChatIdAndCreatedAtBetweenOrderByIdAsc(chatId, startOfDay, endOfDay);

    if (!entities.isEmpty()) {
      final boolean isGroup = entities.get(0).isGroupChat();
      if (!isGroup && (newsChatId == null || !chatId.equals(newsChatId))) {
        final Instant threeHoursAgo = Instant.now().minus(3, ChronoUnit.HOURS);
        entities = entities.stream()
            .filter(e -> e.getCreatedAt().isAfter(threeHoursAgo))
            .toList();
      }
    }

    return entities.stream()
        .map(e -> {
          try {
            return objectMapper.writeValueAsString(e);
          } catch (final JsonProcessingException ex) {
            return null;
          }
        }).filter(Objects::nonNull).toList();
  }
}
