package com.personalfinance.model.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "merchant_aliases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantAlias {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "merchant_rule_id", nullable = false)
  private MerchantRule merchantRule;

  @Column(nullable = false, unique = true)
  private String alias;
}
