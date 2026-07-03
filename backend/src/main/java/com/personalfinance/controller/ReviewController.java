package com.personalfinance.controller;

import com.personalfinance.dto.request.ResolveReviewRequest;
import com.personalfinance.dto.response.ReviewQueueItemResponse;
import com.personalfinance.model.entity.User;
import com.personalfinance.service.ReviewQueueService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

  private final ReviewQueueService reviewQueueService;

  @GetMapping("/pending")
  public ResponseEntity<List<ReviewQueueItemResponse>> pending(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(reviewQueueService.findPending(user.getId()));
  }

  @PostMapping("/{id}/resolve")
  public ResponseEntity<Void> resolve(
      @PathVariable UUID id,
      @Valid @RequestBody ResolveReviewRequest request,
      @AuthenticationPrincipal User user) {
    reviewQueueService.resolve(id, request, user);
    return ResponseEntity.ok().build();
  }
}
