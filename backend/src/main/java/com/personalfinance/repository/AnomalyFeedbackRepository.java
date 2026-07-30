package com.personalfinance.repository;

import com.personalfinance.model.entity.AnomalyFeedback;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnomalyFeedbackRepository extends JpaRepository<AnomalyFeedback, UUID> {

  List<AnomalyFeedback> findByUserId(UUID userId);

  Optional<AnomalyFeedback> findByTransactionIdAndType(UUID transactionId, String type);
}
