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
class ReportControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private UserRepository userRepository;

  private String registerAndGetToken(String email) throws Exception {
    RegisterRequest req = new RegisterRequest("Report User", email, "password123");
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

  @Test
  void top_expenses_returns_200_with_biggest_first() throws Exception {
    String email = "report.top." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    transactionRepository.save(
        Transaction.builder()
            .user(user)
            .description("Small")
            .amount(new BigDecimal("30.00"))
            .type(TransactionType.EXPENSE)
            .date(LocalDate.of(2026, 6, 5))
            .source("MANUAL")
            .build());
    transactionRepository.save(
        Transaction.builder()
            .user(user)
            .description("Big")
            .amount(new BigDecimal("500.00"))
            .type(TransactionType.EXPENSE)
            .date(LocalDate.of(2026, 6, 10))
            .source("MANUAL")
            .build());

    // Same request the frontend makes (year+month+limit).
    mockMvc
        .perform(
            get("/api/reports/top-expenses?year=2026&month=6&limit=10")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].description").value("Big"))
        .andExpect(jsonPath("$[0].amount").value(500.00))
        .andExpect(jsonPath("$[0].date").value("2026-06-10"))
        .andExpect(jsonPath("$[1].description").value("Small"));
  }

  @Test
  void top_expenses_uses_competence_month_like_the_breakdown() throws Exception {
    String email = "report.comp." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    // Purchase dated in May but competing in June (fatura paid in June) must show under June —
    // exactly the rows the category-breakdown groups, so the two cards can never disagree.
    transactionRepository.save(
        Transaction.builder()
            .user(user)
            .description("Compra no crédito")
            .amount(new BigDecimal("120.00"))
            .type(TransactionType.EXPENSE)
            .date(LocalDate.of(2026, 5, 20))
            .competenceDate(LocalDate.of(2026, 6, 1))
            .source("FATURA")
            .build());

    mockMvc
        .perform(
            get("/api/reports/top-expenses?year=2026&month=6&limit=10")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].description").value("Compra no crédito"))
        .andExpect(jsonPath("$[0].amount").value(120.00));
  }
}
