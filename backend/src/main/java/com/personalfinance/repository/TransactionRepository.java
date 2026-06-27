package com.personalfinance.repository;

import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByUserIdOrderByDateDesc(UUID userId, Pageable pageable);

    Page<Transaction> findByUserIdAndTypeOrderByDateDesc(UUID userId, TransactionType type, Pageable pageable);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.user.id = :userId AND t.type = :type AND t.date BETWEEN :start AND :end")
    BigDecimal sumByUserIdAndTypeAndDateBetween(
            @Param("userId") UUID userId,
            @Param("type") TransactionType type,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );
}
