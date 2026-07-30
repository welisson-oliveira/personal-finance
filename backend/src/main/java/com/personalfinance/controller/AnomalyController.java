package com.personalfinance.controller;

import com.personalfinance.dto.request.AnomalyFeedbackRequest;
import com.personalfinance.dto.response.AnomalyResponse;
import com.personalfinance.model.entity.User;
import com.personalfinance.service.AnomalyDetectionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

  private final AnomalyDetectionService anomalyDetectionService;

  @GetMapping
  public ResponseEntity<List<AnomalyResponse>> list(
      @RequestParam(defaultValue = "false") boolean includeResolved,
      @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(anomalyDetectionService.findAnomalies(user, includeResolved));
  }

  @PostMapping("/feedback")
  public ResponseEntity<Void> feedback(
      @RequestBody @Valid AnomalyFeedbackRequest request, @AuthenticationPrincipal User user) {
    anomalyDetectionService.submitFeedback(request, user);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/feedback")
  public ResponseEntity<Void> reopen(
      @RequestParam UUID transactionId,
      @RequestParam String type,
      @AuthenticationPrincipal User user) {
    anomalyDetectionService.reopen(transactionId, type, user);
    return ResponseEntity.noContent().build();
  }
}
