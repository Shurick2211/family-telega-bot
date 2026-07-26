package org.nimko.com.repository;

import java.time.Instant;
import java.util.List;
import org.nimko.com.entity.DaylySummaryChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailySummaryChatRepository extends JpaRepository<DaylySummaryChatEntity, Long> {

  List<DaylySummaryChatEntity> findByChatIdAndCreatedAtBetweenOrderByIdAsc(Long chatId,
      Instant createdAtFrom, Instant createdAtTo);

  List<Long> findDistinctChatIdByCreatedAtBetween(Instant createdAtFrom, Instant createdAtTo);

}
