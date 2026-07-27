package com.personalfinance.controller;

import com.personalfinance.dto.request.CreateMerchantRuleRequest;
import com.personalfinance.dto.response.MerchantRuleResponse;
import com.personalfinance.model.entity.User;
import com.personalfinance.service.MerchantRuleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/merchant-rules")
@RequiredArgsConstructor
public class MerchantRuleController {

  private final MerchantRuleService merchantRuleService;

  @GetMapping
  public ResponseEntity<List<MerchantRuleResponse>> list(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(merchantRuleService.findAll(user.getId()));
  }

  @PostMapping
  public ResponseEntity<MerchantRuleResponse> create(
      @RequestBody @Valid CreateMerchantRuleRequest request, @AuthenticationPrincipal User user) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(merchantRuleService.create(request, user));
  }

  @PutMapping("/{id}")
  public ResponseEntity<MerchantRuleResponse> update(
      @PathVariable UUID id,
      @RequestBody @Valid CreateMerchantRuleRequest request,
      @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(merchantRuleService.update(id, request, user));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
    merchantRuleService.delete(id, user);
    return ResponseEntity.noContent().build();
  }
}
