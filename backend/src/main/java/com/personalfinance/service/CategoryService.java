package com.personalfinance.service;

import com.personalfinance.dto.request.CreateCategoryRequest;
import com.personalfinance.dto.response.CategoryResponse;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.CategoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private final CategoryRepository categoryRepository;

  /**
   * Only the user's own categories (top-levels + subcategories) — everything they see is theirs.
   */
  public List<CategoryResponse> findAll(UUID userId) {
    return categoryRepository.findByUserId(userId).stream().map(this::toResponse).toList();
  }

  @Transactional
  public CategoryResponse create(CreateCategoryRequest request, User user) {
    Category parent = resolveParent(request.parentId(), user.getId(), null);
    Category category =
        Category.builder()
            .name(request.name())
            .icon(request.icon())
            .color(request.color())
            .user(user)
            .parent(parent)
            .build();
    return toResponse(categoryRepository.save(category));
  }

  @Transactional
  public CategoryResponse update(UUID id, CreateCategoryRequest request, User user) {
    Category category = findOwnedByUser(id, user.getId());
    category.setName(request.name());
    category.setIcon(request.icon());
    category.setColor(request.color());
    category.setParent(resolveParent(request.parentId(), user.getId(), id));
    return toResponse(categoryRepository.save(category));
  }

  @Transactional
  public void delete(UUID id, User user) {
    Category category = findOwnedByUser(id, user.getId());
    categoryRepository.delete(category);
  }

  /** Validates the parent belongs to the user and doesn't create a cycle (only one level deep). */
  private Category resolveParent(UUID parentId, UUID userId, UUID selfId) {
    if (parentId == null) return null;
    if (parentId.equals(selfId)) {
      throw new IllegalArgumentException("A category cannot be its own parent");
    }
    Category parent = findOwnedByUser(parentId, userId);
    if (parent.getParent() != null) {
      throw new IllegalArgumentException("Subcategories cannot have their own subcategories");
    }
    return parent;
  }

  private Category findOwnedByUser(UUID id, UUID userId) {
    return categoryRepository
        .findById(id)
        .filter(c -> c.getUser() != null && c.getUser().getId().equals(userId))
        .orElseThrow(() -> new IllegalArgumentException("Category not found or not owned by user"));
  }

  private CategoryResponse toResponse(Category c) {
    Category parent = c.getParent();
    return new CategoryResponse(
        c.getId(),
        c.getName(),
        c.getIcon(),
        c.getColor(),
        c.getUser() == null,
        parent != null ? parent.getId() : null,
        parent != null ? parent.getName() : null);
  }
}
