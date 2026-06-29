package com.personalfinance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalfinance.dto.request.LoginRequest;
import com.personalfinance.dto.request.RegisterRequest;
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
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void register_withValidData_returns201AndToken() throws Exception {
    RegisterRequest request = new RegisterRequest("Alice", "alice@example.com", "secret123");

    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.user.email").value("alice@example.com"))
        .andExpect(jsonPath("$.user.name").value("Alice"));
  }

  @Test
  void login_withCorrectCredentials_returns200AndToken() throws Exception {
    RegisterRequest register = new RegisterRequest("Bob", "bob@example.com", "secret456");
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
        .andExpect(status().isCreated());

    LoginRequest login = new LoginRequest("bob@example.com", "secret456");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn();

    String token =
        objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    assertThat(token).isNotBlank();
  }

  @Test
  void login_withWrongPassword_returns401() throws Exception {
    RegisterRequest register = new RegisterRequest("Carol", "carol@example.com", "correct123");
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register)))
        .andExpect(status().isCreated());

    LoginRequest login = new LoginRequest("carol@example.com", "wrongpassword");

    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getTransactions_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
  }

  @Test
  void getTransactions_withValidToken_returns200() throws Exception {
    RegisterRequest register = new RegisterRequest("Dave", "dave@example.com", "password789");
    MvcResult regResult =
        mockMvc
            .perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(register)))
            .andExpect(status().isCreated())
            .andReturn();

    String token =
        objectMapper.readTree(regResult.getResponse().getContentAsString()).get("token").asText();

    mockMvc
        .perform(get("/api/transactions").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }
}
