package com.personalfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalfinance.dto.request.CreateTransactionRequest;
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
class TransactionControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private UserRepository userRepository;

  private String registerAndGetToken(String email) throws Exception {
    RegisterRequest req = new RegisterRequest("TX User", email, "password123");
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
  void list_without_token_returns_401() throws Exception {
    mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
  }

  @Test
  void list_with_token_returns_200_empty() throws Exception {
    String token = registerAndGetToken("tx.list." + UUID.randomUUID() + "@example.com");
    mockMvc
        .perform(get("/api/transactions").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void create_manual_transaction_returns_201_with_source_manual() throws Exception {
    String token = registerAndGetToken("tx.create." + UUID.randomUUID() + "@example.com");

    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setDescription("Café da manhã");
    req.setAmount(new BigDecimal("25.00"));
    req.setType("EXPENSE");
    req.setDate(LocalDate.of(2026, 5, 10));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/transactions")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.description").value("Café da manhã"))
            .andExpect(jsonPath("$.source").value("MANUAL"))
            .andReturn();

    var node = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(node.get("id").asText()).isNotBlank();
  }

  @Test
  void update_transaction_returns_200() throws Exception {
    String email = "tx.update." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    Transaction tx =
        transactionRepository.save(
            Transaction.builder()
                .user(user)
                .description("Old desc")
                .amount(new BigDecimal("10.00"))
                .type(TransactionType.EXPENSE)
                .date(LocalDate.of(2026, 5, 1))
                .source("MANUAL")
                .build());

    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setDescription("New desc");
    req.setAmount(new BigDecimal("20.00"));
    req.setType("EXPENSE");
    req.setDate(LocalDate.of(2026, 5, 1));

    mockMvc
        .perform(
            put("/api/transactions/" + tx.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("New desc"))
        .andExpect(jsonPath("$.amount").value(20.00));
  }

  @Test
  void delete_own_transaction_returns_204() throws Exception {
    String email = "tx.delete." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    Transaction tx =
        transactionRepository.save(
            Transaction.builder()
                .user(user)
                .description("To delete")
                .amount(new BigDecimal("5.00"))
                .type(TransactionType.EXPENSE)
                .date(LocalDate.of(2026, 5, 2))
                .source("MANUAL")
                .build());

    mockMvc
        .perform(
            delete("/api/transactions/" + tx.getId()).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    assertThat(transactionRepository.findById(tx.getId())).isEmpty();
  }

  @Test
  void delete_another_users_transaction_returns_403() throws Exception {
    String ownerEmail = "tx.owner." + UUID.randomUUID() + "@example.com";
    String attackerEmail = "tx.attacker." + UUID.randomUUID() + "@example.com";
    registerAndGetToken(ownerEmail);
    String attackerToken = registerAndGetToken(attackerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();

    Transaction tx =
        transactionRepository.save(
            Transaction.builder()
                .user(owner)
                .description("Private tx")
                .amount(new BigDecimal("50.00"))
                .type(TransactionType.EXPENSE)
                .date(LocalDate.of(2026, 5, 3))
                .source("MANUAL")
                .build());

    mockMvc
        .perform(
            delete("/api/transactions/" + tx.getId())
                .header("Authorization", "Bearer " + attackerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void list_by_month_excludes_own_transfer() throws Exception {
    String email = "tx.month." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    User user = userRepository.findByEmail(email).orElseThrow();

    transactionRepository.save(
        Transaction.builder()
            .user(user)
            .description("Normal expense")
            .amount(new BigDecimal("100.00"))
            .type(TransactionType.EXPENSE)
            .date(LocalDate.of(2026, 5, 10))
            .source("MANUAL")
            .build());

    transactionRepository.save(
        Transaction.builder()
            .user(user)
            .description("Transfer to own account")
            .amount(new BigDecimal("500.00"))
            .type(TransactionType.INCOME)
            .incomeType("OWN_TRANSFER")
            .date(LocalDate.of(2026, 5, 15))
            .source("EXTRATO")
            .build());

    mockMvc
        .perform(get("/api/transactions?month=2026-05").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].description").value("Normal expense"));
  }

  @Test
  void shared_transaction_fields_returned_correctly() throws Exception {
    String token = registerAndGetToken("tx.shared." + UUID.randomUUID() + "@example.com");

    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setDescription("Jantar compartilhado");
    req.setAmount(new BigDecimal("200.00"));
    req.setType("EXPENSE");
    req.setDate(LocalDate.of(2026, 5, 20));
    req.setShared(true);
    req.setTotalAmount(new BigDecimal("800.00"));
    req.setUserShare(new BigDecimal("200.00"));

    mockMvc
        .perform(
            post("/api/transactions")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.shared").value(true))
        .andExpect(jsonPath("$.totalAmount").value(800.00))
        .andExpect(jsonPath("$.userShare").value(200.00));
  }

  @Test
  void invalid_endpoint_returns_json_error() throws Exception {
    String token = registerAndGetToken("tx.404." + UUID.randomUUID() + "@example.com");
    mockMvc
        .perform(get("/api/nonexistent-endpoint").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").exists());
  }
}
