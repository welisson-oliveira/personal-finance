package com.personalfinance.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

  @GetMapping
  public ResponseEntity<List<Object>> list() {
    return ResponseEntity.ok(List.of());
  }
}
