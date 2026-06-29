package com.personalfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalfinance.dto.request.RegisterRequest;
import com.personalfinance.dto.request.ResolveReviewRequest;
import com.personalfinance.model.entity.*;
import com.personalfinance.repository.*;
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
class ReviewControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ReviewQueueRepository reviewQueueRepository;
  @Autowired private MerchantRuleRepository merchantRuleRepository;
  @Autowired private MerchantAliasRepository merchantAliasRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private CategoryRepository categoryRepository;

  private String registerAndGetToken(String email) throws Exception {
    RegisterRequest req = new RegisterRequest("Review User", email, "password123");
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
  void pending_with_no_items_returns_empty_list() throws Exception {
    String token = registerAndGetToken("review.empty." + UUID.randomUUID() + "@example.com");

    mockMvc
        .perform(get("/api/review/pending").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void pending_without_token_returns_401() throws Exception {
    mockMvc.perform(get("/api/review/pending")).andExpect(status().isUnauthorized());
  }

  @Test
  void resolve_marks_item_reviewed_and_creates_merchant_rule() throws Exception {
    String email = "review.resolve." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    ReviewQueue item =
        reviewQueueRepository.save(
            ReviewQueue.builder()
                .user(user)
                .rawDescription("Lanchonete Nova")
                .normalizedDescription("Lanchonete Nova")
                .amount(new BigDecimal("35.50"))
                .transactionDate(LocalDate.of(2026, 5, 15))
                .status("PENDING")
                .build());

    ResolveReviewRequest request =
        new ResolveReviewRequest(null, "NON_ESSENTIAL", "Lanchonete Nova");

    mockMvc
        .perform(
            post("/api/review/" + item.getId() + "/resolve")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    ReviewQueue updated = reviewQueueRepository.findById(item.getId()).orElseThrow();
    assertThat(updated.getStatus()).isEqualTo("REVIEWED");
    assertThat(updated.getResolvedAt()).isNotNull();

    var rules =
        merchantRuleRepository.findUserRuleByNormalizedName("Lanchonete Nova", user.getId());
    assertThat(rules).isPresent();
    assertThat(rules.get().getConfidence()).isEqualTo(100);
    assertThat(rules.get().getCreatedBy()).isEqualTo("USER");
    assertThat(rules.get().getExpenseType()).isEqualTo("NON_ESSENTIAL");
  }

  @Test
  void resolve_creates_alias_for_raw_description() throws Exception {
    String email = "review.alias." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    ReviewQueue item =
        reviewQueueRepository.save(
            ReviewQueue.builder()
                .user(user)
                .rawDescription("Burguer King *Online")
                .normalizedDescription("Burger King")
                .amount(new BigDecimal("52.00"))
                .transactionDate(LocalDate.of(2026, 5, 16))
                .status("PENDING")
                .build());

    ResolveReviewRequest request = new ResolveReviewRequest(null, "NON_ESSENTIAL", "Burger King");

    mockMvc
        .perform(
            post("/api/review/" + item.getId() + "/resolve")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    var alias = merchantAliasRepository.findByAliasIgnoreCase("Burguer King *Online");
    assertThat(alias).isPresent();
  }

  @Test
  void resolve_on_another_users_item_returns_400() throws Exception {
    String ownerEmail = "review.owner." + UUID.randomUUID() + "@example.com";
    String attackerEmail = "review.attacker." + UUID.randomUUID() + "@example.com";
    registerAndGetToken(ownerEmail);
    String attackerToken = registerAndGetToken(attackerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();

    ReviewQueue item =
        reviewQueueRepository.save(
            ReviewQueue.builder()
                .user(owner)
                .rawDescription("Secret Purchase")
                .normalizedDescription("Secret Purchase")
                .amount(new BigDecimal("99.00"))
                .transactionDate(LocalDate.of(2026, 5, 17))
                .status("PENDING")
                .build());

    ResolveReviewRequest request = new ResolveReviewRequest(null, "ESSENTIAL", "Secret Purchase");

    mockMvc
        .perform(
            post("/api/review/" + item.getId() + "/resolve")
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().is4xxClientError());
  }

  @Test
  void pending_shows_only_pending_items_for_authenticated_user() throws Exception {
    String email = "review.pending." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    reviewQueueRepository.save(
        ReviewQueue.builder()
            .user(user)
            .rawDescription("Shop A")
            .normalizedDescription("Shop A")
            .amount(new BigDecimal("10.00"))
            .transactionDate(LocalDate.of(2026, 5, 1))
            .status("PENDING")
            .build());

    reviewQueueRepository.save(
        ReviewQueue.builder()
            .user(user)
            .rawDescription("Shop B")
            .normalizedDescription("Shop B")
            .amount(new BigDecimal("20.00"))
            .transactionDate(LocalDate.of(2026, 5, 2))
            .status("REVIEWED")
            .build());

    mockMvc
        .perform(get("/api/review/pending").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].rawDescription").value("Shop A"));
  }
}
