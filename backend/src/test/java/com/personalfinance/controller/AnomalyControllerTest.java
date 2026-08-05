package com.personalfinance.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalfinance.dto.request.RegisterRequest;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.TransactionRepository;
import com.personalfinance.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnomalyControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionRepository transactionRepository;

  private String registerAndGetToken(String email) throws Exception {
    RegisterRequest req = new RegisterRequest("Anomaly User", email, "password123");
    MvcResult res =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
  }

  private Transaction seedExpense(User user, String norm, String amount, LocalDate date) {
    return transactionRepository.save(
        Transaction.builder()
            .user(user)
            .description(norm)
            .normalizedDescription(norm)
            .amount(new BigDecimal(amount))
            .type(TransactionType.EXPENSE)
            .date(date)
            .source("MANUAL")
            .build());
  }

  @Test
  void full_lifecycle_of_a_duplicate_anomaly() throws Exception {
    String email = "anom." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    LocalDate now = LocalDate.now();
    String merchant = "Netflix " + UUID.randomUUID();
    seedExpense(user, merchant, "39.90", now.minusDays(10));
    Transaction dup = seedExpense(user, merchant, "39.90", now.minusDays(8));

    // 1. GET lists the duplicate as OPEN.
    mockMvc
        .perform(get("/api/anomalies").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.type == 'DUPLICATE_CHARGE')]").exists())
        .andExpect(jsonPath("$[?(@.transactionId == '" + dup.getId() + "')].status").value("OPEN"));

    // 2. Mark it a false positive → 204.
    mockMvc
        .perform(
            post("/api/anomalies/feedback")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "transactionId", dup.getId().toString(),
                            "type", "DUPLICATE_CHARGE",
                            "status", "FALSE_POSITIVE"))))
        .andExpect(status().isNoContent());

    // 3. Default list no longer shows it.
    mockMvc
        .perform(get("/api/anomalies").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.transactionId == '" + dup.getId() + "')]").doesNotExist());

    // 4. includeResolved surfaces it with the stored status.
    mockMvc
        .perform(
            get("/api/anomalies?includeResolved=true").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.transactionId == '" + dup.getId() + "')].status")
                .value("FALSE_POSITIVE"));

    // 5. Reopen clears the feedback.
    mockMvc
        .perform(
            delete("/api/anomalies/feedback")
                .param("transactionId", dup.getId().toString())
                .param("type", "DUPLICATE_CHARGE")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/anomalies").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$[?(@.transactionId == '" + dup.getId() + "')].status").value("OPEN"));
  }

  @Test
  void feedback_invalidStatus_returns400() throws Exception {
    String token = registerAndGetToken("anom.bad." + UUID.randomUUID() + "@example.com");

    mockMvc
        .perform(
            post("/api/anomalies/feedback")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "transactionId", UUID.randomUUID().toString(),
                            "type", "DUPLICATE_CHARGE",
                            "status", "WHATEVER"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void feedback_onForeignTransaction_returns403() throws Exception {
    String ownerEmail = "anom.owner." + UUID.randomUUID() + "@example.com";
    registerAndGetToken(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
    Transaction owned = seedExpense(owner, "Uber", "20.00", LocalDate.now().minusDays(1));

    String attackerToken = registerAndGetToken("anom.att." + UUID.randomUUID() + "@example.com");

    mockMvc
        .perform(
            post("/api/anomalies/feedback")
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of(
                            "transactionId", owned.getId().toString(),
                            "type", "AMOUNT_OUTLIER",
                            "status", "FALSE_POSITIVE"))))
        .andExpect(status().isForbidden());
  }
}
