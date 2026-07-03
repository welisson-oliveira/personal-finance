package com.personalfinance.model.entity;

import com.personalfinance.model.entity.enums.TransactionType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 500)
  private String description;

  @Column(name = "normalized_description", length = 255)
  private String normalizedDescription;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TransactionType type;

  @Column(name = "income_type", length = 30)
  private String incomeType;

  @Column(name = "budget_group", length = 20)
  private String budgetGroup;

  @Column(nullable = false)
  private LocalDate date;

  private String notes;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "category_id")
  private Category category;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "import_session_id")
  private ImportSession importSession;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "known_person_id")
  private KnownPerson knownPerson;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(length = 20)
  @Builder.Default
  private String source = "MANUAL";

  @Column(name = "card_holder", length = 100)
  private String cardHolder;

  @Column(name = "installment_info", length = 20)
  private String installmentInfo;

  @Builder.Default private boolean shared = false;

  @Column(name = "total_amount", precision = 19, scale = 2)
  private BigDecimal totalAmount;

  @Column(name = "user_share", precision = 19, scale = 2)
  private BigDecimal userShare;

  @CreationTimestamp
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
