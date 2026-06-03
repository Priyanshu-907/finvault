package com.finvault.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvault.account.dto.AccountDtos.*;
import com.finvault.account.entity.Account.AccountType;
import com.finvault.account.repository.AccountRepository;
import com.finvault.account.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final UUID TEST_USER_ID = UUID.randomUUID();
    private static String testToken;
    private static String createdAccountNumber;

    @BeforeEach
    void setupToken() {
        if (testToken == null) {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            testToken = Jwts.builder()
                    .subject("test@finvault.com")
                    .claim("userId", TEST_USER_ID.toString())
                    .claim("roles", List.of("ROLE_USER"))
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 900_000))
                    .signWith(key)
                    .compact();
        }
    }

    @Test
    @Order(1)
    void shouldCreateSavingsAccount() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest(
                AccountType.SAVINGS, new BigDecimal("5000.00"), "INR");

        MvcResult result = mockMvc.perform(post("/api/v1/accounts")
                .header("Authorization", "Bearer " + testToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountType").value("SAVINGS"))
                .andExpect(jsonPath("$.balance").value(5000.0))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.accountNumber").isNotEmpty())
                .andReturn();

        AccountResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), AccountResponse.class);
        createdAccountNumber = response.accountNumber();
        assertThat(createdAccountNumber).startsWith("FV10");
    }

    @Test
    @Order(2)
    void shouldGetMyAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/accounts")
                .header("Authorization", "Bearer " + testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].userId").value(TEST_USER_ID.toString()));
    }

    @Test
    @Order(3)
    void shouldCheckBalance() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{acc}/balance", createdAccountNumber)
                .header("Authorization", "Bearer " + testToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(5000.0));
    }

    @Test
    @Order(4)
    void shouldDeposit() throws Exception {
        DepositRequest request = new DepositRequest(new BigDecimal("1000.00"));

        mockMvc.perform(post("/api/v1/accounts/{acc}/deposit", createdAccountNumber)
                .header("Authorization", "Bearer " + testToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(6000.0));
    }

    @Test
    @Order(5)
    void shouldWithdraw() throws Exception {
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("500.00"));

        mockMvc.perform(post("/api/v1/accounts/{acc}/withdraw", createdAccountNumber)
                .header("Authorization", "Bearer " + testToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(5500.0));
    }

    @Test
    @Order(6)
    void shouldRejectOverdraft() throws Exception {
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("999999.00"));

        mockMvc.perform(post("/api/v1/accounts/{acc}/withdraw", createdAccountNumber)
                .header("Authorization", "Bearer " + testToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @Order(7)
    void shouldRejectUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{acc}", createdAccountNumber))
                .andExpect(status().isForbidden());
    }
}
