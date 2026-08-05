package com.personalfinance.repository;

import com.personalfinance.model.entity.ImportSession;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportSessionRepository extends JpaRepository<ImportSession, UUID> {

  List<ImportSession> findByUserIdOrderByCreatedAtDesc(UUID userId);

  @Query(
      "SELECT s FROM ImportSession s "
          + "WHERE s.user.id = :userId "
          + "AND s.documentType = 'FATURA' "
          + "AND s.status = 'CONFIRMED' "
          + "AND s.periodEnd BETWEEN :start AND :end")
  List<ImportSession> findConfirmedFaturaByUserAndPeriodEndBetween(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
