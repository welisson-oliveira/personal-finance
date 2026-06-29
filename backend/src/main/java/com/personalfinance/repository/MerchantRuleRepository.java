package com.personalfinance.repository;

import com.personalfinance.model.entity.MerchantRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantRuleRepository extends JpaRepository<MerchantRule, UUID> {

  @Query(
      "SELECT r FROM MerchantRule r LEFT JOIN FETCH r.category "
          + "WHERE LOWER(r.normalizedName) = LOWER(:name) AND r.user IS NULL "
          + "ORDER BY r.confidence DESC")
  Optional<MerchantRule> findGlobalByNormalizedName(@Param("name") String name);

  @Query(
      "SELECT r FROM MerchantRule r LEFT JOIN FETCH r.category "
          + "WHERE LOWER(r.normalizedName) = LOWER(:name) AND r.user.id = :userId "
          + "ORDER BY r.confidence DESC")
  Optional<MerchantRule> findUserRuleByNormalizedName(
      @Param("name") String name, @Param("userId") UUID userId);

  @Query(
      "SELECT r FROM MerchantRule r LEFT JOIN FETCH r.category "
          + "WHERE r.user IS NULL OR r.user.id = :userId "
          + "ORDER BY r.user.id NULLS LAST, r.normalizedName")
  List<MerchantRule> findAllVisibleToUser(@Param("userId") UUID userId);
}
