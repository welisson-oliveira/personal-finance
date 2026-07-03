package com.personalfinance.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "merchant_display_names")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantDisplayName {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "normalized_name", nullable = false)
  private String normalizedName;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
