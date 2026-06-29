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

  public List<CategoryResponse> findAll(UUID userId) {
    return categoryRepository.findByUserIdOrUserIsNull(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public CategoryResponse create(CreateCategoryRequest request, User user) {
    Category category =
        Category.builder()
            .name(request.name())
            .icon(request.icon())
            .color(request.color())
            .user(user)
            .build();
    return toResponse(categoryRepository.save(category));
  }

  @Transactional
  public CategoryResponse update(UUID id, CreateCategoryRequest request, User user) {
    Category category = findOwnedByUser(id, user.getId());
    category.setName(request.name());
    category.setIcon(request.icon());
    category.setColor(request.color());
    return toResponse(categoryRepository.save(category));
  }

  @Transactional
  public void delete(UUID id, User user) {
    Category category = findOwnedByUser(id, user.getId());
    categoryRepository.delete(category);
  }

  private Category findOwnedByUser(UUID id, UUID userId) {
    return categoryRepository
        .findById(id)
        .filter(c -> c.getUser() != null && c.getUser().getId().equals(userId))
        .orElseThrow(() -> new IllegalArgumentException("Category not found or not owned by user"));
  }

  private CategoryResponse toResponse(Category c) {
    return new CategoryResponse(
        c.getId(), c.getName(), c.getIcon(), c.getColor(), c.getUser() == null);
  }
}
