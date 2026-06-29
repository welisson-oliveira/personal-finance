package com.personalfinance.repository;

import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

  Page<Transaction> findByUserIdOrderByDateDesc(UUID userId, Pageable pageable);

  Page<Transaction> findByUserIdAndTypeOrderByDateDesc(
      UUID userId, TransactionType type, Pageable pageable);

  @Query(
      "SELECT t FROM Transaction t WHERE t.user.id = :userId "
          + "AND t.date BETWEEN :start AND :end "
          + "AND (t.incomeType IS NULL OR t.incomeType <> 'OWN_TRANSFER') "
          + "ORDER BY t.date DESC")
  Page<Transaction> findByUserIdAndMonthExcludingOwnTransfer(
      @Param("userId") UUID userId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end,
      Pageable pageable);

  List<Transaction> findByImportSessionIdAndUserId(UUID importSessionId, UUID userId);

  @Query(
      "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = :type AND t.incomeType = :incomeType "
          + "AND t.date BETWEEN :start AND :end")
  BigDecimal sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
      @Param("userId") UUID userId,
      @Param("type") TransactionType type,
      @Param("incomeType") String incomeType,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);

  @Query(
      "SELECT COALESCE(SUM(CASE WHEN t.shared = true AND t.userShare IS NOT NULL THEN t.userShare ELSE t.amount END), 0) "
          + "FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.budgetGroup = :budgetGroup "
          + "AND t.date BETWEEN :start AND :end")
  BigDecimal sumExpenseByBudgetGroupAndDateBetween(
      @Param("userId") UUID userId,
      @Param("budgetGroup") String budgetGroup,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);

  @Query(
      "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = :type AND t.date BETWEEN :start AND :end")
  BigDecimal sumByUserIdAndTypeAndDateBetween(
      @Param("userId") UUID userId,
      @Param("type") TransactionType type,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);

  @Query(
      "SELECT t FROM Transaction t LEFT JOIN FETCH t.category "
          + "WHERE t.user.id = :userId AND t.type = 'EXPENSE' "
          + "AND t.date BETWEEN :start AND :end "
          + "AND t.incomeType IS NULL")
  List<Transaction> findExpensesWithCategoryInPeriod(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "SELECT COUNT(t) FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = 'EXPENSE' "
          + "AND t.date BETWEEN :start AND :end")
  long countExpensesInPeriod(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "SELECT COUNT(t) FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = 'EXPENSE' "
          + "AND LOWER(t.description) LIKE '%pix%' "
          + "AND t.date BETWEEN :start AND :end")
  long countPixEnviadosInPeriod(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "SELECT COUNT(t) FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = 'INCOME' "
          + "AND LOWER(t.description) LIKE '%pix%' "
          + "AND t.date BETWEEN :start AND :end")
  long countPixRecebidosInPeriod(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
