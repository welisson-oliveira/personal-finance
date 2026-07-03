package com.personalfinance.controller;

import com.personalfinance.dto.request.CreateTransactionRequest;
import com.personalfinance.dto.response.TransactionResponse;
import com.personalfinance.model.entity.User;
import com.personalfinance.service.TransactionService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

  private final TransactionService transactionService;

  @GetMapping
  public ResponseEntity<Page<TransactionResponse>> list(
      @RequestParam(required = false) String month,
      @RequestParam(required = false) String type,
      @PageableDefault(size = 50, sort = "date") Pageable pageable,
      @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(transactionService.findAll(user.getId(), month, type, pageable));
  }

  @PostMapping
  public ResponseEntity<TransactionResponse> create(
      @Valid @RequestBody CreateTransactionRequest request, @AuthenticationPrincipal User user) {
    return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.create(request, user));
  }

  @PutMapping("/{id}")
  public ResponseEntity<TransactionResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody CreateTransactionRequest request,
      @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(transactionService.update(id, request, user));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
    transactionService.delete(id, user);
    return ResponseEntity.noContent().build();
  }
}
