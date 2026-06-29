package com.personalfinance.controller;

import com.personalfinance.dto.request.LoginRequest;
import com.personalfinance.dto.request.RegisterRequest;
import com.personalfinance.dto.response.AuthResponse;
import com.personalfinance.dto.response.UserResponse;
import com.personalfinance.model.entity.User;
import com.personalfinance.service.JwtService;
import com.personalfinance.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final UserService userService;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
    User user = userService.register(request);
    String token = jwtService.generateToken(user);
    return ResponseEntity.status(HttpStatus.CREATED).body(buildResponse(token, user));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.email(), request.password()));
    User user = (User) userService.loadUserByUsername(request.email());
    String token = jwtService.generateToken(user);
    return ResponseEntity.ok(buildResponse(token, user));
  }

  private AuthResponse buildResponse(String token, User user) {
    return new AuthResponse(token, new UserResponse(user.getId(), user.getName(), user.getEmail()));
  }
}
