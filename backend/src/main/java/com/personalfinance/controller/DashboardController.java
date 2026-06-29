package com.personalfinance.controller;

import com.personalfinance.dto.response.DashboardResponse;
import com.personalfinance.model.entity.User;
import com.personalfinance.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

  private final DashboardService dashboardService;

  @GetMapping("/monthly")
  public ResponseEntity<DashboardResponse> monthly(
      @RequestParam int year, @RequestParam int month, @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(dashboardService.getMonthly(user.getId(), year, month));
  }
}
