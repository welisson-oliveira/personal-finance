package com.personalfinance.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalfinance.dto.request.RegisterRequest;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.MerchantRule;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.MerchantRuleRepository;
import com.personalfinance.repository.UserRepository;
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
class MerchantRuleControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private MerchantRuleRepository merchantRuleRepository;
  @Autowired private CategoryRepository categoryRepository;

  private String registerAndGetToken(String email) throws Exception {
    RegisterRequest req = new RegisterRequest("Rule User", email, "password123");
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

  private String json(Map<String, Object> body) throws Exception {
    return objectMapper.writeValueAsString(body);
  }

  @Test
  void create_expenseRule_returns201_withFields() throws Exception {
    String email = "rule.create." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);

    mockMvc
        .perform(
            post("/api/merchant-rules")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "merchantName", "Padaria do Zé",
                            "type", "EXPENSE",
                            "expenseType", "ESSENTIAL",
                            "ignored", false))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.merchantName").value("Padaria do Zé"))
        .andExpect(jsonPath("$.type").value("EXPENSE"))
        .andExpect(jsonPath("$.expenseType").value("ESSENTIAL"))
        .andExpect(jsonPath("$.global").value(false))
        .andExpect(jsonPath("$.ignored").value(false));
  }

  @Test
  void create_investmentRule_carriesDirection_andNoCategory() throws Exception {
    String email = "rule.inv." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);

    mockMvc
        .perform(
            post("/api/merchant-rules")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "merchantName", "Corretora XP",
                            "type", "INVESTMENT",
                            "investmentDirection", "CONTRIBUTION",
                            "ignored", false))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("INVESTMENT"))
        .andExpect(jsonPath("$.investmentDirection").value("CONTRIBUTION"))
        .andExpect(jsonPath("$.categoryId").doesNotExist());
  }

  @Test
  void create_blankMerchantName_returns400() throws Exception {
    String token = registerAndGetToken("rule.blank." + UUID.randomUUID() + "@example.com");

    mockMvc
        .perform(
            post("/api/merchant-rules")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("merchantName", "", "type", "EXPENSE", "ignored", false))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_invalidType_returns400() throws Exception {
    String token = registerAndGetToken("rule.badtype." + UUID.randomUUID() + "@example.com");

    mockMvc
        .perform(
            post("/api/merchant-rules")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("merchantName", "X", "type", "SPENDING", "ignored", false))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_duplicateUserRule_returns400() throws Exception {
    String token = registerAndGetToken("rule.dup." + UUID.randomUUID() + "@example.com");
    String body = json(Map.of("merchantName", "iFood", "type", "EXPENSE", "ignored", false));

    mockMvc
        .perform(
            post("/api/merchant-rules")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/merchant-rules")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_unknownCategory_returns400() throws Exception {
    String token = registerAndGetToken("rule.badcat." + UUID.randomUUID() + "@example.com");

    mockMvc
        .perform(
            post("/api/merchant-rules")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "merchantName",
                            "X",
                            "type",
                            "EXPENSE",
                            "categoryId",
                            UUID.randomUUID().toString(),
                            "ignored",
                            false))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void update_ownRule_returns200_withCategoryResolved() throws Exception {
    String email = "rule.upd." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    Category cat = categoryRepository.save(Category.builder().name("Mercado").user(user).build());
    MerchantRule rule =
        merchantRuleRepository.save(
            MerchantRule.builder()
                .user(user)
                .merchantName("old")
                .normalizedName("mercado x")
                .expenseType("NON_ESSENTIAL")
                .createdBy("USER")
                .build());

    mockMvc
        .perform(
            put("/api/merchant-rules/" + rule.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "merchantName", "Mercado X",
                            "type", "EXPENSE",
                            "categoryId", cat.getId().toString(),
                            "expenseType", "ESSENTIAL",
                            "ignored", false))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.merchantName").value("Mercado X"))
        .andExpect(jsonPath("$.categoryName").value("Mercado"))
        .andExpect(jsonPath("$.expenseType").value("ESSENTIAL"));
  }

  @Test
  void update_globalRule_creates_personal_override_and_leaves_global() throws Exception {
    String email = "rule.override." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    MerchantRule global =
        merchantRuleRepository.save(
            MerchantRule.builder()
                .user(null)
                .merchantName("Uber")
                .normalizedName("uber " + UUID.randomUUID())
                .expenseType("NON_ESSENTIAL")
                .createdBy("SYSTEM")
                .build());

    mockMvc
        .perform(
            put("/api/merchant-rules/" + global.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        Map.of(
                            "merchantName", "Uber",
                            "type", "EXPENSE",
                            "expenseType", "ESSENTIAL",
                            "ignored", false))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.global").value(false))
        .andExpect(jsonPath("$.expenseType").value("ESSENTIAL"));

    // The global rule is untouched.
    MerchantRule stillGlobal = merchantRuleRepository.findById(global.getId()).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(stillGlobal.getExpenseType())
        .isEqualTo("NON_ESSENTIAL");
    org.assertj.core.api.Assertions.assertThat(stillGlobal.getUser()).isNull();
  }

  @Test
  void update_globalRule_isForbidden_toDelete() throws Exception {
    String token = registerAndGetToken("rule.delglobal." + UUID.randomUUID() + "@example.com");
    MerchantRule global =
        merchantRuleRepository.save(
            MerchantRule.builder()
                .user(null)
                .merchantName("Netflix")
                .normalizedName("netflix " + UUID.randomUUID())
                .expenseType("NON_ESSENTIAL")
                .createdBy("SYSTEM")
                .build());

    mockMvc
        .perform(
            delete("/api/merchant-rules/" + global.getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void update_and_delete_otherUsersRule_isForbidden() throws Exception {
    String ownerEmail = "rule.owner." + UUID.randomUUID() + "@example.com";
    registerAndGetToken(ownerEmail);
    User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
    MerchantRule foreign =
        merchantRuleRepository.save(
            MerchantRule.builder()
                .user(owner)
                .merchantName("Spotify")
                .normalizedName("spotify " + UUID.randomUUID())
                .expenseType("NON_ESSENTIAL")
                .createdBy("USER")
                .build());

    String attackerToken =
        registerAndGetToken("rule.attacker." + UUID.randomUUID() + "@example.com");

    mockMvc
        .perform(
            put("/api/merchant-rules/" + foreign.getId())
                .header("Authorization", "Bearer " + attackerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("merchantName", "X", "type", "EXPENSE", "ignored", false))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            delete("/api/merchant-rules/" + foreign.getId())
                .header("Authorization", "Bearer " + attackerToken))
        .andExpect(status().isForbidden());
  }

  @Test
  void delete_ownRule_returns204_and_removesFromList() throws Exception {
    String email = "rule.del." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    MerchantRule rule =
        merchantRuleRepository.save(
            MerchantRule.builder()
                .user(user)
                .merchantName("Amazon")
                .normalizedName("amazon " + UUID.randomUUID())
                .expenseType("NON_ESSENTIAL")
                .createdBy("USER")
                .build());

    mockMvc
        .perform(
            delete("/api/merchant-rules/" + rule.getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(merchantRuleRepository.findById(rule.getId()))
        .isEmpty();
  }

  @Test
  void list_returns_userAndGlobalRules() throws Exception {
    String email = "rule.list." + UUID.randomUUID() + "@example.com";
    String token = registerAndGetToken(email);
    User user = userRepository.findByEmail(email).orElseThrow();
    merchantRuleRepository.save(
        MerchantRule.builder()
            .user(user)
            .merchantName("Mine")
            .normalizedName("mine " + UUID.randomUUID())
            .expenseType("ESSENTIAL")
            .createdBy("USER")
            .build());

    mockMvc
        .perform(get("/api/merchant-rules").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }
}
