package com.personalfinance.repository;

import com.personalfinance.model.entity.Category;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

  /**
   * User's own + global categories, alphabetically (case-insensitive). Legacy — see findByUserId.
   */
  @Query(
      "SELECT c FROM Category c WHERE c.user.id = :userId OR c.user IS NULL ORDER BY LOWER(c.name)")
  List<Category> findByUserIdOrUserIsNull(@Param("userId") UUID userId);

  /**
   * The user's own categories only (Option 2: everything the user sees is theirs), sorted by name.
   */
  @Query(
      "SELECT c FROM Category c LEFT JOIN FETCH c.parent WHERE c.user.id = :userId ORDER BY LOWER(c.name)")
  List<Category> findByUserId(@Param("userId") UUID userId);

  boolean existsByUserId(UUID userId);

  /**
   * Resolve one of the user's categories by name (case-insensitive) — used by import translation.
   */
  @Query(
      "SELECT c FROM Category c WHERE c.user.id = :userId AND LOWER(c.name) = LOWER(:name) ORDER BY LOWER(c.name)")
  List<Category> findByUserIdAndNameIgnoreCase(
      @Param("userId") UUID userId, @Param("name") String name);

  /** Direct children of a category (one level) — used by budget-goal roll-up. */
  List<Category> findByParentId(UUID parentId);

  /** All global (seed/template) categories — targets of global merchant rules. */
  @Query("SELECT c FROM Category c WHERE c.user IS NULL")
  List<Category> findAllGlobal();

  /**
   * One of the user's categories by name, preferring a top-level over a subcategory of that name.
   */
  default Optional<Category> findFirstByUserIdAndName(UUID userId, String name) {
    return findByUserIdAndNameIgnoreCase(userId, name).stream()
        .min(Comparator.comparing(c -> c.getParent() != null));
  }
}
