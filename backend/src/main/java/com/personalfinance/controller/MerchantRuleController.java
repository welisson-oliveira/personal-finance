package com.personalfinance.controller;

import com.personalfinance.dto.response.MerchantRuleResponse;
import com.personalfinance.model.entity.MerchantRule;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.MerchantRuleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/merchant-rules")
@RequiredArgsConstructor
public class MerchantRuleController {

  private final MerchantRuleRepository merchantRuleRepository;

  @GetMapping
  public ResponseEntity<List<MerchantRuleResponse>> list(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(
        merchantRuleRepository.findAllVisibleToUser(user.getId()).stream()
            .map(this::toResponse)
            .toList());
  }

  private MerchantRuleResponse toResponse(MerchantRule r) {
    return new MerchantRuleResponse(
        r.getId(),
        r.getMerchantName(),
        r.getNormalizedName(),
        r.getCategory() != null ? r.getCategory().getId() : null,
        r.getCategory() != null ? r.getCategory().getName() : null,
        r.getSubcategory(),
        r.getExpenseType(),
        r.getConfidence(),
        r.getCreatedBy(),
        r.getUser() == null);
  }
}
