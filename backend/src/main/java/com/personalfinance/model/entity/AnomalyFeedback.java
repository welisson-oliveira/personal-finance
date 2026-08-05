package com.personalfinance.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The user's verdict on a detected transaction anomaly. Anomalies themselves are recomputed from
 * the data on every read; only this feedback is persisted, keyed by (transaction, type).
 */
@Entity
@Table(
    name = "anomaly_feedback",
    uniqueConstraints = @UniqueConstraint(columnNames = {"transaction_id", "type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnomalyFeedback {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  /** Anomaly type: AMOUNT_OUTLIER | DUPLICATE_CHARGE. */
  @Column(nullable = false)
  private String type;

  /** Verdict: FALSE_POSITIVE | ACKNOWLEDGED. */
  @Column(nullable = false)
  private String status;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
