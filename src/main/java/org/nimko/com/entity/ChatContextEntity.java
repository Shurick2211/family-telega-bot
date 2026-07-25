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
@Table(name = "chat_context", indexes = {
    @Index(name = "chat_id_idx", columnList = "chat_id"),
    @Index(name = "message_id_idx", columnList = "message_id")
})
public class ChatContextEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(name = "chat_id", nullable = false)
  private Long chatId;
  @Column(name = "user_name")
  private String userName;
  @Column(name = "name")
  private String name;
  @Column(name = "message_id")
  private int  messageId;
  @Column(name = "message", columnDefinition = "TEXT")
  private String message;
  @Column(name = "created_at", nullable = false)
  @CreationTimestamp
  private Instant createdAt;
}
