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
import java.util.List;
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
class BudgetGoalControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private TransactionRepository transactionRepository;

  private record Auth(String token, User user) {}

  private Auth register(String email) throws Exception {
    RegisterRequest req = new RegisterRequest("Goal User", email, "password123");
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

  @Test
  void suggestions_for_a_user_with_no_data_returns_200() throws Exception {
    Auth auth = register("goal.empty." + UUID.randomUUID() + "@example.com");

    mockMvc
        .perform(
            get("/api/budget-goals/suggestions?year=2026&month=7")
                .header("Authorization", "Bearer " + auth.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.buckets").isArray());
  }

  @Test
  void suggestions_with_history_returns_per_bucket_categories() throws Exception {
    Auth auth = register("goal.data." + UUID.randomUUID() + "@example.com");
    UUID userId = auth.user().getId();
    List<Category> cats = categoryRepository.findByUserId(userId);
    Category cat = cats.isEmpty() ? newCategory(auth.user()) : cats.get(0);

    // Three months of the same essential expense in the window (Apr–Jun for month=7).
    for (LocalDate d :
        List.of(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 5, 10), LocalDate.of(2026, 6, 10))) {
      transactionRepository.save(
          Transaction.builder()
              .user(auth.user())
              .description("Aluguel")
              .amount(new BigDecimal("1200.00"))
              .type(TransactionType.EXPENSE)
              .budgetGroup("ESSENTIAL")
              .category(cat)
              .date(d)
              .source("MANUAL")
              .build());
    }

    mockMvc
        .perform(
            get("/api/budget-goals/suggestions?year=2026&month=7")
                .header("Authorization", "Bearer " + auth.token()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.buckets[?(@.group == 'ESSENTIAL')].categories[0].suggestedAmount")
                .exists());
  }

  @Test
  void suggestions_with_production_like_data_returns_200() throws Exception {
    Auth auth = register("goal.prod." + UUID.randomUUID() + "@example.com");
    User user = auth.user();
    UUID userId = user.getId();

    Category parent =
        categoryRepository.save(
            Category.builder().user(user).name("Alimentação").icon("restaurant").build());
    Category sub =
        categoryRepository.save(
            Category.builder().user(user).name("Mercado").parent(parent).build());
    Category lazer = categoryRepository.save(Category.builder().user(user).name("Lazer").build());

    // Shared expense with a user share, on a subcategory, with a competence date ≠ purchase date.
    transactionRepository.save(
        Transaction.builder()
            .user(user)
            .description("Mercado dividido")
            .amount(new BigDecimal("400.00"))
            .shared(true)
            .userShare(new BigDecimal("200.00"))
            .type(TransactionType.EXPENSE)
            .budgetGroup("ESSENTIAL")
            .category(sub)
            .date(LocalDate.of(2026, 5, 28))
            .competenceDate(LocalDate.of(2026, 6, 5))
            .source("FATURA")
            .build());
    for (LocalDate d : List.of(LocalDate.of(2026, 4, 8), LocalDate.of(2026, 6, 8))) {
      transactionRepository.save(
          Transaction.builder()
              .user(user)
              .description("Cinema")
              .amount(new BigDecimal("90.00"))
              .type(TransactionType.EXPENSE)
              .budgetGroup("NON_ESSENTIAL")
              .category(lazer)
              .date(d)
              .source("EXTRATO")
              .build());
    }

    mockMvc
        .perform(
            get("/api/budget-goals/suggestions?year=2026&month=7")
                .header("Authorization", "Bearer " + auth.token()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rendaBase").exists());
  }

  @Test
  void goal_spent_is_net_of_reimbursements_in_the_same_category() throws Exception {
    Auth auth = register("goal.reimb." + UUID.randomUUID() + "@example.com");
    User user = auth.user();
    Category contas = categoryRepository.save(Category.builder().user(user).name("Contas").build());
    createGoal(auth.token(), contas.getId(), "1000.00");

    LocalDate d = LocalDate.of(2026, 6, 10);
    transactionRepository.save(
        Transaction.builder()
            .user(user)
            .description("EDP + SABESP")
            .amount(new BigDecimal("2240.00"))
            .type(TransactionType.EXPENSE)
            .budgetGroup("ESSENTIAL")
            .category(contas)
            .date(d)
            .source("MANUAL")
            .build());
    transactionRepository.save(
        Transaction.builder()
            .user(user)
            .description("Rateio moradores")
            .amount(new BigDecimal("1341.00"))
            .type(TransactionType.INCOME)
            .reimbursement(true)
            .budgetGroup("ESSENTIAL")
            .category(contas)
            .date(d)
            .source("MANUAL")
            .build());

    mockMvc
        .perform(
            get("/api/budget-goals?year=2026&month=6")
                .header("Authorization", "Bearer " + auth.token()))
        .andExpect(status().isOk())
        // spent = 2240 − 1341 = 899, within the 1000 goal (not over).
        .andExpect(jsonPath("$[0].categoryName").value("Contas"))
        .andExpect(jsonPath("$[0].spent").value(closeTo(899.0, 0.001)))
        .andExpect(jsonPath("$[0].remaining").value(closeTo(101.0, 0.001)));
  }

  private void createGoal(String token, UUID categoryId, String amount) throws Exception {
    mockMvc
        .perform(
            post("/api/budget-goals")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categoryId\":\"" + categoryId + "\",\"amount\":" + amount + "}"))
        .andExpect(status().isCreated());
  }

  private Category newCategory(User user) {
    return categoryRepository.save(
        Category.builder().user(user).name("Moradia").icon("home").color("#000000").build());
  }
}
