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

  @Column(name = "default_income_type", nullable = false)
  @Builder.Default
  private String defaultIncomeType = "REIMBURSEMENT";

  @Column(name = "default_label")
  private String defaultLabel;

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;
}
