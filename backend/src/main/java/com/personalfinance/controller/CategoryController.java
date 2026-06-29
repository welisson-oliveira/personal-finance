package com.personalfinance.controller;

import com.personalfinance.dto.request.CreateCategoryRequest;
import com.personalfinance.dto.response.CategoryResponse;
import com.personalfinance.model.entity.User;
import com.personalfinance.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

  private final CategoryService categoryService;

  @GetMapping
  public ResponseEntity<List<CategoryResponse>> list(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(categoryService.findAll(user.getId()));
  }

  @PostMapping
  public ResponseEntity<CategoryResponse> create(
      @RequestBody @Valid CreateCategoryRequest request, @AuthenticationPrincipal User user) {
    return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request, user));
  }

  @PutMapping("/{id}")
  public ResponseEntity<CategoryResponse> update(
      @PathVariable UUID id,
      @RequestBody @Valid CreateCategoryRequest request,
      @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(categoryService.update(id, request, user));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
    categoryService.delete(id, user);
    return ResponseEntity.noContent().build();
  }
}
