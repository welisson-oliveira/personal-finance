package com.personalfinance.repository;

import com.personalfinance.model.entity.ReviewQueue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewQueueRepository extends JpaRepository<ReviewQueue, UUID> {

  List<ReviewQueue> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);

  List<ReviewQueue> findByUserIdAndNormalizedDescriptionIgnoreCaseAndStatus(
      UUID userId, String normalizedDescription, String status);

  int countByImportSessionIdAndStatus(UUID importSessionId, String status);

  void deleteByImportSessionId(UUID importSessionId);
}
