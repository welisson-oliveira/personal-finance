package com.personalfinance.controller;

import com.personalfinance.dto.response.ImportPreviewResponse;
import com.personalfinance.model.entity.ImportSession;
import com.personalfinance.model.entity.User;
import com.personalfinance.service.TransactionImportService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

  private final TransactionImportService importService;

  @PostMapping(value = "/parse", consumes = "multipart/form-data")
  public ResponseEntity<ImportPreviewResponse> parse(
      @RequestParam("file") MultipartFile file,
      @RequestParam("documentType") String documentType,
      @AuthenticationPrincipal User user)
      throws IOException {
    ImportPreviewResponse preview = importService.parseAndPreview(file, documentType, user);
    return ResponseEntity.status(HttpStatus.CREATED).body(preview);
  }

  @PostMapping("/{id}/confirm")
  public ResponseEntity<Void> confirm(@PathVariable UUID id, @AuthenticationPrincipal User user) {
    importService.confirm(id, user);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<Void> cancel(@PathVariable UUID id, @AuthenticationPrincipal User user) {
    importService.cancel(id, user);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/history")
  public ResponseEntity<List<ImportSession>> history(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(importService.getHistory(user.getId()));
  }
}
