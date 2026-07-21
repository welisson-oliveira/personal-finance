package com.personalfinance.service;

import com.personalfinance.dto.request.BulkBudgetGoalRequest;
import com.personalfinance.dto.request.CreateBudgetGoalRequest;
import com.personalfinance.dto.response.BudgetGoalResponse;
import com.personalfinance.dto.response.BudgetSuggestionResponse;
import com.personalfinance.dto.response.BudgetSuggestionResponse.BucketSuggestion;
import com.personalfinance.dto.response.BudgetSuggestionResponse.CategorySuggestion;
import com.personalfinance.dto.response.BudgetSuggestionResponse.InvestmentSuggestion;
import com.personalfinance.model.entity.BudgetGoal;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.BudgetGoalRepository;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BudgetGoalService {

  private final BudgetGoalRepository budgetGoalRepository;
  private final CategoryRepository categoryRepository;
  private final TransactionRepository transactionRepository;

  /** Lists the user's goals with spending progress for the given month. */
  @Transactional(readOnly = true)
  public List<BudgetGoalResponse> findAll(UUID userId, int year, int month) {
    YearMonth ym = YearMonth.of(year, month);
    LocalDate start = ym.atDay(1);
    LocalDate end = ym.atEndOfMonth();
    return budgetGoalRepository.findByUserId(userId).stream()
        .map(goal -> toResponse(goal, userId, start, end))
        .toList();
  }

  @Transactional
  public BudgetGoalResponse create(CreateBudgetGoalRequest request, User user) {
    Category category = resolveCategory(request.categoryId());
    budgetGoalRepository
        .findByUserIdAndCategoryId(user.getId(), category.getId())
        .ifPresent(
            g -> {
              throw new IllegalArgumentException("A goal already exists for this category");
            });
    BudgetGoal goal =
        BudgetGoal.builder().user(user).category(category).amount(request.amount()).build();
    BudgetGoal saved = budgetGoalRepository.save(goal);
    YearMonth now = YearMonth.now();
    return toResponse(saved, user.getId(), now.atDay(1), now.atEndOfMonth());
  }

  @Transactional
  public BudgetGoalResponse update(UUID id, CreateBudgetGoalRequest request, User user) {
    BudgetGoal goal = findOwned(id, user.getId());
    goal.setAmount(request.amount());
    BudgetGoal saved = budgetGoalRepository.save(goal);
    YearMonth now = YearMonth.now();
    return toResponse(saved, user.getId(), now.atDay(1), now.atEndOfMonth());
  }

  @Transactional
  public void delete(UUID id, User user) {
    BudgetGoal goal = findOwned(id, user.getId());
    budgetGoalRepository.delete(goal);
  }

  /** Create-or-update several goals at once (used by "Aplicar metas sugeridas"). */
  @Transactional
  public List<BudgetGoalResponse> bulkUpsert(List<BulkBudgetGoalRequest.Item> items, User user) {
    YearMonth now = YearMonth.now();
    List<BudgetGoalResponse> saved = new ArrayList<>();
    for (BulkBudgetGoalRequest.Item item : items) {
      Category category = resolveCategory(item.categoryId());
      BudgetGoal goal =
          budgetGoalRepository
              .findByUserIdAndCategoryId(user.getId(), category.getId())
              .orElseGet(() -> BudgetGoal.builder().user(user).category(category).build());
      goal.setAmount(item.amount());
      saved.add(
          toResponse(
              budgetGoalRepository.save(goal), user.getId(), now.atDay(1), now.atEndOfMonth()));
    }
    return saved;
  }

  // ---------------------------------------------------------------------------
  // Sugestão de metas (50/30/20)
  // ---------------------------------------------------------------------------

  private static final int WINDOW_MONTHS = 3;
  private static final BigDecimal ESSENTIAL_FRACTION = new BigDecimal("0.50");
  private static final BigDecimal NON_ESSENTIAL_FRACTION = new BigDecimal("0.30");
  private static final BigDecimal INVESTMENT_FRACTION = new BigDecimal("0.20");
  private static final BigDecimal ROUND_STEP = BigDecimal.TEN;

  /**
   * Suggests per-category budget goals from the user's own spending over the last {@link
   * #WINDOW_MONTHS} months, fitted to the 50/30/20 caps: each category is estimated by the median
   * of its monthly totals, placed in its dominant bucket, and — when the bucket's historical total
   * exceeds the cap — scaled down proportionally so the suggested goals add up to the cap. The 20%
   * investment floor is returned as a single monthly-contribution target.
   */
  @Transactional(readOnly = true)
  public BudgetSuggestionResponse suggest(User user, int year, int month) {
    UUID userId = user.getId();
    YearMonth ym = YearMonth.of(year, month);
    BigDecimal base = resolveIncomeBase(user, userId, ym);

    List<Transaction> history =
        transactionRepository.findExpensesWithCategoryInPeriod(
            userId, ym.minusMonths(WINDOW_MONTHS).atDay(1), ym.minusMonths(1).atEndOfMonth());

    Set<UUID> withGoals =
        budgetGoalRepository.findByUserId(userId).stream()
            .map(g -> g.getCategory().getId())
            .collect(Collectors.toSet());

    Map<UUID, List<Transaction>> byCategory =
        history.stream()
            .filter(t -> t.getCategory() != null && isBudgetGroup(t.getBudgetGroup()))
            .collect(Collectors.groupingBy(t -> t.getCategory().getId()));

    Map<String, List<CategorySuggestion>> rawByGroup = new HashMap<>();
    rawByGroup.put("ESSENTIAL", new ArrayList<>());
    rawByGroup.put("NON_ESSENTIAL", new ArrayList<>());

    byCategory.forEach(
        (catId, txs) -> {
          BigDecimal median = medianMonthly(txs, ym);
          if (median.compareTo(BigDecimal.ZERO) <= 0) return; // present in ≤1 month → skip
          Category cat = txs.get(0).getCategory();
          rawByGroup
              .get(dominantGroup(txs))
              .add(
                  new CategorySuggestion(
                      cat.getId(),
                      cat.getName(),
                      cat.getIcon(),
                      cat.getColor(),
                      median,
                      median,
                      withGoals.contains(cat.getId())));
        });

    List<BucketSuggestion> buckets =
        List.of(
            buildBucket(
                "ESSENTIAL", base.multiply(ESSENTIAL_FRACTION), rawByGroup.get("ESSENTIAL")),
            buildBucket(
                "NON_ESSENTIAL",
                base.multiply(NON_ESSENTIAL_FRACTION),
                rawByGroup.get("NON_ESSENTIAL")));

    BigDecimal invMedian = medianInvestment(userId, ym).max(BigDecimal.ZERO);
    BigDecimal invCap = round(base.multiply(INVESTMENT_FRACTION));
    InvestmentSuggestion investimentos = new InvestmentSuggestion(invCap, round(invMedian), invCap);

    return new BudgetSuggestionResponse(round(base), buckets, investimentos);
  }

  private BucketSuggestion buildBucket(String group, BigDecimal cap, List<CategorySuggestion> raw) {
    BigDecimal historicalTotal =
        raw.stream()
            .map(CategorySuggestion::historicalMonthly)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    boolean overCap = cap.compareTo(BigDecimal.ZERO) > 0 && historicalTotal.compareTo(cap) > 0;
    BigDecimal scale =
        overCap ? cap.divide(historicalTotal, 6, RoundingMode.HALF_UP) : BigDecimal.ONE;

    List<CategorySuggestion> out = new ArrayList<>();
    for (CategorySuggestion c : raw) {
      BigDecimal suggested = round(c.historicalMonthly().multiply(scale));
      if (suggested.compareTo(BigDecimal.ZERO) <= 0) continue; // rounds to nothing → drop
      out.add(
          new CategorySuggestion(
              c.categoryId(),
              c.categoryName(),
              c.categoryIcon(),
              c.categoryColor(),
              round(c.historicalMonthly()),
              suggested,
              c.hasGoal()));
    }
    out.sort(Comparator.comparing(CategorySuggestion::suggestedAmount).reversed());
    BigDecimal suggestedTotal =
        out.stream()
            .map(CategorySuggestion::suggestedAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new BucketSuggestion(
        group, round(cap), round(historicalTotal), suggestedTotal, overCap, out);
  }

  /** Income base for the 50/30/20 caps: the configured salary, else the median monthly income. */
  private BigDecimal resolveIncomeBase(User user, UUID userId, YearMonth ym) {
    BigDecimal salary = user.getMonthlyNetIncome();
    if (salary != null && salary.compareTo(BigDecimal.ZERO) > 0) return salary;
    List<BigDecimal> monthly = new ArrayList<>();
    for (int i = 1; i <= WINDOW_MONTHS; i++) {
      YearMonth m = ym.minusMonths(i);
      monthly.add(
          transactionRepository.sumIncomeByUserIdAndDateBetween(
              userId, m.atDay(1), m.atEndOfMonth()));
    }
    return median(monthly);
  }

  /** Median of a category's monthly totals over the window (0 for months with no spend). */
  private BigDecimal medianMonthly(List<Transaction> txs, YearMonth ym) {
    Map<YearMonth, BigDecimal> perMonth = new HashMap<>();
    for (Transaction t : txs) {
      perMonth.merge(competenceMonth(t), effectiveAmount(t), BigDecimal::add);
    }
    List<BigDecimal> monthly = new ArrayList<>();
    for (int i = 1; i <= WINDOW_MONTHS; i++) {
      monthly.add(perMonth.getOrDefault(ym.minusMonths(i), BigDecimal.ZERO));
    }
    return median(monthly);
  }

  /** Median of the monthly net investment contribution (aportes − resgates) over the window. */
  private BigDecimal medianInvestment(UUID userId, YearMonth ym) {
    List<BigDecimal> monthly = new ArrayList<>();
    for (int i = 1; i <= WINDOW_MONTHS; i++) {
      YearMonth m = ym.minusMonths(i);
      BigDecimal contribution =
          transactionRepository.sumInvestmentByDirectionAndDateBetween(
              userId, "CONTRIBUTION", m.atDay(1), m.atEndOfMonth());
      BigDecimal redemption =
          transactionRepository.sumInvestmentByDirectionAndDateBetween(
              userId, "REDEMPTION", m.atDay(1), m.atEndOfMonth());
      monthly.add(contribution.subtract(redemption));
    }
    return median(monthly);
  }

  private String dominantGroup(List<Transaction> txs) {
    BigDecimal essential = BigDecimal.ZERO;
    BigDecimal nonEssential = BigDecimal.ZERO;
    for (Transaction t : txs) {
      if ("ESSENTIAL".equals(t.getBudgetGroup())) essential = essential.add(effectiveAmount(t));
      else if ("NON_ESSENTIAL".equals(t.getBudgetGroup()))
        nonEssential = nonEssential.add(effectiveAmount(t));
    }
    return essential.compareTo(nonEssential) >= 0 ? "ESSENTIAL" : "NON_ESSENTIAL";
  }

  private boolean isBudgetGroup(String group) {
    return "ESSENTIAL".equals(group) || "NON_ESSENTIAL".equals(group);
  }

  private BigDecimal median(List<BigDecimal> values) {
    if (values.isEmpty()) return BigDecimal.ZERO;
    List<BigDecimal> sorted = new ArrayList<>(values);
    Collections.sort(sorted);
    int n = sorted.size();
    int mid = n / 2;
    if (n % 2 == 1) return sorted.get(mid);
    return sorted
        .get(mid - 1)
        .add(sorted.get(mid))
        .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
  }

  /** Rounds to the nearest R$10 for friendly goal amounts. */
  private BigDecimal round(BigDecimal value) {
    if (value == null) return BigDecimal.ZERO;
    return value.divide(ROUND_STEP, 0, RoundingMode.HALF_UP).multiply(ROUND_STEP);
  }

  private YearMonth competenceMonth(Transaction t) {
    LocalDate d = t.getCompetenceDate() != null ? t.getCompetenceDate() : t.getDate();
    return YearMonth.from(d);
  }

  private BigDecimal effectiveAmount(Transaction t) {
    if (t.isShared() && t.getUserShare() != null) return t.getUserShare();
    return t.getAmount();
  }

  private BudgetGoal findOwned(UUID id, UUID userId) {
    BudgetGoal goal =
        budgetGoalRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Budget goal not found"));
    if (!goal.getUser().getId().equals(userId)) {
      throw new AccessDeniedException("Access denied");
    }
    return goal;
  }

  private Category resolveCategory(UUID categoryId) {
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(() -> new IllegalArgumentException("Category not found"));
  }

  private BudgetGoalResponse toResponse(
      BudgetGoal goal, UUID userId, LocalDate start, LocalDate end) {
    Category category = goal.getCategory();
    // A goal on a top-level category also counts spending in its subcategories (roll-up).
    List<UUID> categoryIds = new java.util.ArrayList<>();
    categoryIds.add(category.getId());
    categoryRepository.findByParentId(category.getId()).forEach(c -> categoryIds.add(c.getId()));
    BigDecimal spent =
        transactionRepository.sumExpenseByCategoryIdsAndDateBetween(
            userId, categoryIds, start, end);
    BigDecimal amount = goal.getAmount();
    BigDecimal remaining = amount.subtract(spent);
    BigDecimal percentage =
        amount.compareTo(BigDecimal.ZERO) > 0
            ? spent.multiply(BigDecimal.valueOf(100)).divide(amount, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
    return new BudgetGoalResponse(
        goal.getId(),
        category.getId(),
        category.getName(),
        category.getIcon(),
        category.getColor(),
        amount,
        spent,
        remaining,
        percentage);
  }
}
