package com.personalfinance.controller;

import com.personalfinance.dto.request.UpdateProfileRequest;
import com.personalfinance.dto.response.UserResponse;
import com.personalfinance.model.entity.User;
import com.personalfinance.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(toResponse(user));
  }

  @PutMapping("/me")
  public ResponseEntity<UserResponse> updateProfile(
      @AuthenticationPrincipal User user, @RequestBody @Valid UpdateProfileRequest request) {
    User updated = userService.updateProfile(user.getId(), request);
    return ResponseEntity.ok(toResponse(updated));
  }

  private UserResponse toResponse(User user) {
    return new UserResponse(
        user.getId(), user.getName(), user.getEmail(), user.getMonthlyNetIncome());
  }
}
