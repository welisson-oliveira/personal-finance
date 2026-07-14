package com.personalfinance.repository;

import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.enums.TransactionType;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public class TransactionSpecifications {

  private TransactionSpecifications() {}

  public static Specification<Transaction> forUser(UUID userId) {
    return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
  }

  public static Specification<Transaction> inDateRange(LocalDate start, LocalDate end) {
    return (root, query, cb) -> {
      if (start == null || end == null) return cb.conjunction();
      return cb.between(root.get("date"), start, end);
    };
  }

  public static Specification<Transaction> ofType(TransactionType type) {
    return (root, query, cb) -> {
      if (type == null) return cb.conjunction();
      return cb.equal(root.get("type"), type);
    };
  }

  public static Specification<Transaction> inCategory(UUID categoryId) {
    return (root, query, cb) -> {
      if (categoryId == null) return cb.conjunction();
      return cb.equal(root.get("category").get("id"), categoryId);
    };
  }

  public static Specification<Transaction> excludingIgnored() {
    return (root, query, cb) -> cb.isFalse(root.get("ignored"));
  }

  public static Specification<Transaction> needingReview(Boolean needsReview) {
    return (root, query, cb) -> {
      if (needsReview == null || !needsReview) return cb.conjunction();
      return cb.isTrue(root.get("needsReview"));
    };
  }

  /** Case-insensitive match across description, normalized description and notes (apelido). */
  public static Specification<Transaction> descriptionContains(String search) {
    return (root, query, cb) -> {
      if (search == null || search.isBlank()) return cb.conjunction();
      String like = "%" + search.trim().toLowerCase() + "%";
      return cb.or(
          cb.like(cb.lower(root.get("description")), like),
          cb.like(cb.lower(root.get("normalizedDescription")), like),
          cb.like(cb.lower(root.get("notes")), like));
    };
  }

  public static Specification<Transaction> ofBudgetGroup(String budgetGroup) {
    return (root, query, cb) -> {
      if (budgetGroup == null || budgetGroup.isBlank()) return cb.conjunction();
      return cb.equal(root.get("budgetGroup"), budgetGroup);
    };
  }
}
