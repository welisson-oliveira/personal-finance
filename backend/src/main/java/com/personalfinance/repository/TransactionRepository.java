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
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository
    extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

  Page<Transaction> findByUserIdOrderByDateDesc(UUID userId, Pageable pageable);

  long countByImportSessionId(UUID importSessionId);

  Page<Transaction> findByUserIdAndTypeOrderByDateDesc(
      UUID userId, TransactionType type, Pageable pageable);

  List<Transaction> findByImportSessionIdAndUserId(UUID importSessionId, UUID userId);

  void deleteByImportSessionId(UUID importSessionId);

  @Query(
      "SELECT t FROM Transaction t WHERE t.user.id = :userId "
          + "AND (t.normalizedDescription = :name OR (t.normalizedDescription IS NULL AND t.description = :name))")
  List<Transaction> findByUserIdAndEffectiveName(
      @Param("userId") UUID userId, @Param("name") String name);

  @Query(
      "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = 'INCOME' AND t.ignored = false "
          + "AND COALESCE(t.competenceDate, t.date) BETWEEN :start AND :end")
  BigDecimal sumIncomeByUserIdAndDateBetween(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = 'INVESTMENT' AND t.ignored = false "
          + "AND t.investmentDirection = :direction "
          + "AND COALESCE(t.competenceDate, t.date) BETWEEN :start AND :end")
  BigDecimal sumInvestmentByDirectionAndDateBetween(
      @Param("userId") UUID userId,
      @Param("direction") String direction,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);

  @Query(
      "SELECT COALESCE(SUM(CASE WHEN t.shared = true AND t.userShare IS NOT NULL THEN t.userShare ELSE t.amount END), 0) "
          + "FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.ignored = false "
          + "AND t.budgetGroup = :budgetGroup "
          + "AND COALESCE(t.competenceDate, t.date) BETWEEN :start AND :end")
  BigDecimal sumExpenseByBudgetGroupAndDateBetween(
      @Param("userId") UUID userId,
      @Param("budgetGroup") String budgetGroup,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);

  @Query(
      "SELECT COALESCE(SUM(CASE WHEN t.shared = true AND t.userShare IS NOT NULL THEN t.userShare ELSE t.amount END), 0) "
          + "FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.ignored = false "
          + "AND t.category.id = :categoryId "
          + "AND COALESCE(t.competenceDate, t.date) BETWEEN :start AND :end")
  BigDecimal sumExpenseByCategoryAndDateBetween(
      @Param("userId") UUID userId,
      @Param("categoryId") UUID categoryId,
      @Param("start") LocalDate start,
      @Param("end") LocalDate end);

  @Query(
      "SELECT t FROM Transaction t LEFT JOIN FETCH t.category "
          + "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.ignored = false "
          + "AND COALESCE(t.competenceDate, t.date) BETWEEN :start AND :end")
  List<Transaction> findExpensesWithCategoryInPeriod(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "SELECT COUNT(t) FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.ignored = false "
          + "AND COALESCE(t.competenceDate, t.date) BETWEEN :start AND :end")
  long countExpensesInPeriod(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "SELECT COUNT(t) FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.ignored = false "
          + "AND LOWER(t.description) LIKE '%pix%' "
          + "AND COALESCE(t.competenceDate, t.date) BETWEEN :start AND :end")
  long countPixEnviadosInPeriod(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "SELECT COUNT(t) FROM Transaction t "
          + "WHERE t.user.id = :userId AND t.type = 'INCOME' AND t.ignored = false "
          + "AND LOWER(t.description) LIKE '%pix%' "
          + "AND COALESCE(t.competenceDate, t.date) BETWEEN :start AND :end")
  long countPixRecebidosInPeriod(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "SELECT t FROM Transaction t "
          + "WHERE t.user.id = :userId "
          + "AND t.source = 'EXTRATO' "
          + "AND LOWER(t.description) LIKE 'pagamento de fatura%' "
          + "AND t.date BETWEEN :start AND :end")
  List<Transaction> findBillPaymentsByUserAndDateBetween(
      @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(
      "SELECT t FROM Transaction t "
          + "WHERE t.user.id = :userId "
          + "AND t.source = 'EXTRATO' "
          + "AND LOWER(t.description) LIKE 'pagamento de fatura%' "
          + "ORDER BY t.date DESC")
  List<Transaction> findBillPaymentsByUser(@Param("userId") UUID userId);

  /** Net total of an import session's items (expenses minus estornos) ≈ the invoice amount paid. */
  @Query(
      "SELECT COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount "
          + "WHEN t.type = 'INCOME' THEN -t.amount ELSE 0 END), 0) "
          + "FROM Transaction t WHERE t.importSession.id = :sessionId")
  BigDecimal sumNetByImportSession(@Param("sessionId") UUID sessionId);
}
