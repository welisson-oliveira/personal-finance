package com.personalfinance.repository;

import com.personalfinance.model.entity.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

  /** User's own + global categories, alphabetically (case-insensitive) — the order the UI shows. */
  @Query(
      "SELECT c FROM Category c WHERE c.user.id = :userId OR c.user IS NULL ORDER BY LOWER(c.name)")
  List<Category> findByUserIdOrUserIsNull(@Param("userId") UUID userId);
}
