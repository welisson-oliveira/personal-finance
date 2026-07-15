package com.personalfinance.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "merchant_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantRule {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "merchant_name", nullable = false)
  private String merchantName;

  @Column(name = "normalized_name", nullable = false)
  private String normalizedName;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private Category category;

  private String subcategory;

  @Column(name = "expense_type", nullable = false)
  @Builder.Default
  private String expenseType = "NON_ESSENTIAL";

  /** Transaction type to force (INCOME/EXPENSE/INVESTMENT); null on legacy expense-only rules. */
  private String type;

  /** Whether matched transactions should be ignored (e.g. a recurring own-account transfer). */
  @Column(nullable = false)
  @Builder.Default
  private boolean ignored = false;

  @Column(name = "investment_direction")
  private String investmentDirection;

  @Builder.Default private Integer confidence = 100;

  @Column(name = "created_by", nullable = false)
  @Builder.Default
  private String createdBy = "SYSTEM";

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
