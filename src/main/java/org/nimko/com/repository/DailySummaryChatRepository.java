package org.nimko.com.repository;

import java.time.Instant;
import java.util.List;
import org.nimko.com.entity.DaylySummaryChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DailySummaryChatRepository extends JpaRepository<DaylySummaryChatEntity, Long> {

  List<DaylySummaryChatEntity> findByChatIdAndCreatedAtBetweenOrderByIdAsc(Long chatId,
      Instant createdAtFrom, Instant createdAtTo);

  @Query("SELECT DISTINCT d.chatId FROM DaylySummaryChatEntity d WHERE d.createdAt BETWEEN :createdAtFrom AND :createdAtTo")
  List<Long> findDistinctChatIdByCreatedAtBetween(
      @Param("createdAtFrom") Instant createdAtFrom,
      @Param("createdAtTo") Instant createdAtTo);

}
