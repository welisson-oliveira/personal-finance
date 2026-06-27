package com.personalfinance.repository;

import com.personalfinance.model.entity.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
  List<Category> findByUserIdOrUserIsNull(UUID userId);
}
