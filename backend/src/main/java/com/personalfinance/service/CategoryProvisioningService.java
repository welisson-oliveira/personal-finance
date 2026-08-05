package com.personalfinance.service;

import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Copies the {@link DefaultCategories} starter tree into a user's own account (Option 2: the user
 * owns everything and can edit/delete it). Idempotent — does nothing if the user already has
 * categories, so it is safe to call on every registration and on startup for existing users.
 */
@Service
@RequiredArgsConstructor
public class CategoryProvisioningService {

  private final CategoryRepository categoryRepository;

  @Transactional
  public void provisionDefaults(User user) {
    if (categoryRepository.existsByUserId(user.getId())) return;
    for (DefaultCategories.Group group : DefaultCategories.TREE) {
      Category parent =
          categoryRepository.save(
              Category.builder()
                  .name(group.name())
                  .icon(group.icon())
                  .color(group.color())
                  .user(user)
                  .build());
      for (String child : group.children()) {
        categoryRepository.save(
            Category.builder()
                .name(child)
                .icon("subdirectory_arrow_right")
                .color(group.color())
                .user(user)
                .parent(parent)
                .build());
      }
    }
  }
}
