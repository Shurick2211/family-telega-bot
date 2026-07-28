package org.nimko.com.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "dayly_summary_chat", indexes = {
    @Index(name = "dayly_summary_chat_id_idx", columnList = "chat_id"),
    @Index(name = "dayly_summary_created_at_idx", columnList = "created_at")
})
public class DaylySummaryChatEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "chat_id", nullable = false)
  private Long chatId;
  @Column(name = "text", columnDefinition = "TEXT")
  private String text;
  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;
}
