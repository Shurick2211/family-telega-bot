package org.nimko.com.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.nimko.com.entity.ChatContextEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatContextRepository extends JpaRepository<ChatContextEntity, Long> {

  Optional<ChatContextEntity> findByChatIdAndMessageId(Long chatId, int messageId);

  List<ChatContextEntity> findByChatIdAndCreatedAtBetweenOrderByIdAsc(Long chatId,
      Instant createdAtFrom, Instant createdAtTo);

  static List<String> getTodayContext(final ChatContextRepository chatContextRepository,
      final ObjectMapper objectMapper, final Long chatId) {
    final LocalDate today = LocalDate.now(ZoneId.systemDefault());
    final Instant startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
    final Instant endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    return chatContextRepository
        .findByChatIdAndCreatedAtBetweenOrderByIdAsc(chatId, startOfDay, endOfDay).stream()
        .map(e -> {
          try {
            return objectMapper.writeValueAsString(e);
          } catch (final JsonProcessingException ex) {
            return null;
          }
        }).filter(Objects::nonNull).toList();
  }
}
