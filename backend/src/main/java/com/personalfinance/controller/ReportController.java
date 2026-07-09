package com.personalfinance.controller;

import com.personalfinance.dto.response.CategoryTotalResponse;
import com.personalfinance.dto.response.MonthlyPointResponse;
import com.personalfinance.model.entity.User;
import com.personalfinance.service.ReportService;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

  private final ReportService reportService;

  @GetMapping("/monthly-evolution")
  public ResponseEntity<List<MonthlyPointResponse>> monthlyEvolution(
      @RequestParam(defaultValue = "6") int months, @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(reportService.monthlyEvolution(user.getId(), months));
  }

  @GetMapping("/category-breakdown")
  public ResponseEntity<List<CategoryTotalResponse>> categoryBreakdown(
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month,
      @AuthenticationPrincipal User user) {
    YearMonth ym = (year != null && month != null) ? YearMonth.of(year, month) : YearMonth.now();
    return ResponseEntity.ok(
        reportService.categoryBreakdown(user.getId(), ym.getYear(), ym.getMonthValue()));
  }
}
