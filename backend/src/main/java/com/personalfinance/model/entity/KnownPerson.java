package com.personalfinance.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "known_persons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnownPerson {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String relationship;

  /** How income from this person is treated: INCOME | IGNORE | ALWAYS_REVIEW. */
  @Column(name = "default_treatment", nullable = false)
  @Builder.Default
  private String defaultTreatment = "INCOME";

  @Column(name = "default_label")
  private String defaultLabel;

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;
}
