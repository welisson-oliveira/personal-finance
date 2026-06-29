package com.personalfinance.controller;

import com.personalfinance.dto.request.CreateKnownPersonRequest;
import com.personalfinance.dto.response.KnownPersonResponse;
import com.personalfinance.model.entity.User;
import com.personalfinance.service.KnownPersonService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/known-persons")
@RequiredArgsConstructor
public class KnownPersonController {

  private final KnownPersonService knownPersonService;

  @GetMapping
  public ResponseEntity<List<KnownPersonResponse>> list(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(knownPersonService.findAll(user.getId()));
  }

  @PostMapping
  public ResponseEntity<KnownPersonResponse> create(
      @RequestBody @Valid CreateKnownPersonRequest request, @AuthenticationPrincipal User user) {
    return ResponseEntity.status(HttpStatus.CREATED).body(knownPersonService.create(request, user));
  }

  @PutMapping("/{id}")
  public ResponseEntity<KnownPersonResponse> update(
      @PathVariable UUID id,
      @RequestBody @Valid CreateKnownPersonRequest request,
      @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(knownPersonService.update(id, request, user));
  }

  @PatchMapping("/{id}/deactivate")
  public ResponseEntity<Void> deactivate(
      @PathVariable UUID id, @AuthenticationPrincipal User user) {
    knownPersonService.deactivate(id, user);
    return ResponseEntity.noContent().build();
  }
}
