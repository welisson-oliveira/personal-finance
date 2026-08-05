package com.personalfinance.repository;

import com.personalfinance.model.entity.BudgetGoal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetGoalRepository extends JpaRepository<BudgetGoal, UUID> {

  @Query("SELECT g FROM BudgetGoal g LEFT JOIN FETCH g.category WHERE g.user.id = :userId")
  List<BudgetGoal> findByUserId(UUID userId);

  Optional<BudgetGoal> findByUserIdAndCategoryId(UUID userId, UUID categoryId);
}
