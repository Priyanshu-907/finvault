package com.finvault.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finvault.transaction.client.AccountClient;
import com.finvault.transaction.dto.TransactionDtos.*;
import com.finvault.transaction.entity.Transaction.TransactionStatus;
import com.finvault.transaction.repository.TransactionRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"transaction-events"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TransactionRepository transactionRepository;

    // Mock the Feign client so we don't need account-service running in tests
    @MockBean AccountClient accountClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final UUID TEST_USER = UUID.randomUUID();
    private static String token;
    private static UUID createdTxnId;
    private static final String FROM_ACC = "FV10000000000001";
    private static final String TO_ACC   = "FV10000000000002";

    @BeforeEach
    void setup() {
        if (token == null) {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            token = Jwts.builder()
                    .subject("user@finvault.com")
                    .claim("userId", TEST_USER.toString())
                    .claim("roles", List.of("ROLE_USER"))
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 900_000))
                    .signWith(key)
                    .compact();
        }
    }

    @Test
    @Order(1)
    void shouldCompleteTransferWhenAccountServiceSucceeds() throws Exception {
        doNothing().when(accountClient).applyBalanceUpdate(any());

        TransferRequest request = new TransferRequest(
                FROM_ACC, TO_ACC, new BigDecimal("1000.00"), "INR", "Test transfer", null);

        MvcResult result = mockMvc.perform(post("/api/v1/transactions/transfer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.referenceId").isNotEmpty())
                .andReturn();

        TransactionResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(), TransactionResponse.class);
        createdTxnId = response.id();
    }

    @Test
    @Order(2)
    void shouldMarkFailedWhenAccountServiceThrows() throws Exception {
        doThrow(new RuntimeException("Insufficient funds"))
                .when(accountClient).applyBalanceUpdate(any());

        TransferRequest request = new TransferRequest(
                FROM_ACC, TO_ACC, new BigDecimal("50000.00"), "INR", "Should fail", null);

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").isNotEmpty());
    }

    @Test
    @Order(3)
    void shouldRejectDuplicateIdempotencyKey() throws Exception {
        doNothing().when(accountClient).applyBalanceUpdate(any());

        String idempotencyKey = "unique-key-" + UUID.randomUUID();
        TransferRequest first = new TransferRequest(
                FROM_ACC, TO_ACC, new BigDecimal("100.00"), "INR", "First", idempotencyKey);

        // First request succeeds
        mockMvc.perform(post("/api/v1/transactions/transfer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // Second request with same idempotency key → 409 Conflict
        mockMvc.perform(post("/api/v1/transactions/transfer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(4)
    void shouldGetTransactionById() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", createdTxnId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdTxnId.toString()));
    }

    @Test
    @Order(5)
    void shouldGetPaginatedHistory() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/history")
                .header("Authorization", "Bearer " + token)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @Order(6)
    void shouldRejectSameAccountTransfer() throws Exception {
        TransferRequest request = new TransferRequest(
                FROM_ACC, FROM_ACC, new BigDecimal("100.00"), "INR", "Self transfer", null);

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
