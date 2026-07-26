package org.nimko.com.repository;

import org.nimko.com.entity.DaylySummaryChatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailySummaryChatRepository extends JpaRepository<DaylySummaryChatEntity, Long> {

}
