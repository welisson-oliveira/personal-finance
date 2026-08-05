package com.personalfinance.controller;

import com.personalfinance.dto.request.BulkBudgetGoalRequest;
import com.personalfinance.dto.request.CreateBudgetGoalRequest;
import com.personalfinance.dto.response.BudgetGoalResponse;
import com.personalfinance.dto.response.BudgetSuggestionResponse;
import com.personalfinance.model.entity.User;
import com.personalfinance.service.BudgetGoalService;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/budget-goals")
@RequiredArgsConstructor
public class BudgetGoalController {

  private final BudgetGoalService budgetGoalService;

  @GetMapping
  public ResponseEntity<List<BudgetGoalResponse>> list(
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month,
      @AuthenticationPrincipal User user) {
    YearMonth ym = (year != null && month != null) ? YearMonth.of(year, month) : YearMonth.now();
    return ResponseEntity.ok(
        budgetGoalService.findAll(user.getId(), ym.getYear(), ym.getMonthValue()));
  }

  @GetMapping("/suggestions")
  public ResponseEntity<BudgetSuggestionResponse> suggestions(
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month,
      @AuthenticationPrincipal User user) {
    YearMonth ym = (year != null && month != null) ? YearMonth.of(year, month) : YearMonth.now();
    return ResponseEntity.ok(budgetGoalService.suggest(user, ym.getYear(), ym.getMonthValue()));
  }

  @PostMapping
  public ResponseEntity<BudgetGoalResponse> create(
      @RequestBody @Valid CreateBudgetGoalRequest request, @AuthenticationPrincipal User user) {
    return ResponseEntity.status(HttpStatus.CREATED).body(budgetGoalService.create(request, user));
  }

  @PostMapping("/bulk")
  public ResponseEntity<List<BudgetGoalResponse>> bulkUpsert(
      @RequestBody @Valid BulkBudgetGoalRequest request, @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(budgetGoalService.bulkUpsert(request.goals(), user));
  }

  @PutMapping("/{id}")
  public ResponseEntity<BudgetGoalResponse> update(
      @PathVariable UUID id,
      @RequestBody @Valid CreateBudgetGoalRequest request,
      @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(budgetGoalService.update(id, request, user));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
    budgetGoalService.delete(id, user);
    return ResponseEntity.noContent().build();
  }
}
