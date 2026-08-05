package com.personalfinance.repository;

import com.personalfinance.model.entity.MerchantDisplayName;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantDisplayNameRepository extends JpaRepository<MerchantDisplayName, UUID> {

  Optional<MerchantDisplayName> findByUserIdAndNormalizedName(UUID userId, String normalizedName);
}
