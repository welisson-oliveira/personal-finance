package com.personalfinance.controller;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalfinance.dto.request.RegisterRequest;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.CategoryRepository;
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
class DashboardControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private TransactionRepository transactionRepository;

  private record Auth(String token, User user) {}

  private Auth register(String email) throws Exception {
    RegisterRequest req = new RegisterRequest("Dash User", email, "password123");
    MvcResult res =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    String token =
        objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    return new Auth(token, userRepository.findByEmail(email).orElseThrow());
  }

  /**
   * End-to-end proof of the reimbursement (contra-expense) netting through the real queries: a
   * reimbursement leaves income out and subtracts the expense of its category/group everywhere, and
   * the cash balance still counts it. Salary 5000, "Contas" (ESSENTIAL) expense 2240, flatmate
   * reimbursement 1341 → net cost 899, result 4101.
   */
  @Test
  void monthly_nets_reimbursement_against_its_category_and_group() throws Exception {
    Auth auth = register("dash.reimb." + UUID.randomUUID() + "@example.com");
    User user = auth.user();
    Category contas = categoryRepository.save(Category.builder().user(user).name("Contas").build());

    LocalDate d = LocalDate.of(2026, 6, 10);
    save(user, "Salário", "5000.00", TransactionType.INCOME, null, null, false, d);
    save(user, "EDP + SABESP", "2240.00", TransactionType.EXPENSE, "ESSENTIAL", contas, false, d);
    save(
        user,
        "Rateio contas moradores",
        "1341.00",
        TransactionType.INCOME,
        "ESSENTIAL",
        contas,
        true,
        d);

    mockMvc
        .perform(
            get("/api/dashboard/monthly?year=2026&month=6")
                .header("Authorization", "Bearer " + auth.token()))
        .andExpect(status().isOk())
        // Reimbursement excluded from income.
        .andExpect(jsonPath("$.entradas").value(closeTo(5000.0, 0.001)))
        // Net essential expense = 2240 − 1341.
        .andExpect(jsonPath("$.despesasEssenciais").value(closeTo(899.0, 0.001)))
        .andExpect(jsonPath("$.totalDespesas").value(closeTo(899.0, 0.001)))
        .andExpect(jsonPath("$.resultado").value(closeTo(4101.0, 0.001)))
        // Cash balance still counts the reimbursement as money in: 5000 + 1341 − 2240.
        .andExpect(jsonPath("$.saldoAcumulado").value(closeTo(4101.0, 0.001)))
        // Breakdown shows the net cost of the category.
        .andExpect(jsonPath("$.breakdown.essenciais[0].categoryName").value("Contas"))
        .andExpect(jsonPath("$.breakdown.essenciais[0].total").value(closeTo(899.0, 0.001)))
        // The reimbursement is NOT listed as income in "De onde veio o dinheiro".
        .andExpect(jsonPath("$.entradasBreakdown[?(@.categoryName == 'Contas')]").isEmpty());
  }

  private void save(
      User user,
      String description,
      String amount,
      TransactionType type,
      String budgetGroup,
      Category category,
      boolean reimbursement,
      LocalDate date) {
    transactionRepository.save(
        Transaction.builder()
            .user(user)
            .description(description)
            .amount(new BigDecimal(amount))
            .type(type)
            .budgetGroup(budgetGroup)
            .category(category)
            .reimbursement(reimbursement)
            .date(date)
            .source("MANUAL")
            .build());
  }
}
