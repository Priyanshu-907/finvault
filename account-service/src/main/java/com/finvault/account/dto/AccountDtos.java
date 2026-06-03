package com.finvault.account.dto;

import com.finvault.account.entity.Account.AccountStatus;
import com.finvault.account.entity.Account.AccountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class AccountDtos {

    // ── Requests ──────────────────────────────────────────────────

    public record CreateAccountRequest(
            @NotNull
            AccountType accountType,

            @NotNull @DecimalMin(value = "0.0", inclusive = false)
            @Digits(integer = 15, fraction = 4)
            BigDecimal initialDeposit,

            @NotBlank @Size(min = 3, max = 3)
            String currency
    ) {}

    public record DepositRequest(
            @NotNull @DecimalMin(value = "0.01")
            @Digits(integer = 15, fraction = 4)
            BigDecimal amount
    ) {}

    public record WithdrawRequest(
            @NotNull @DecimalMin(value = "0.01")
            @Digits(integer = 15, fraction = 4)
            BigDecimal amount
    ) {}

    // Used internally by transaction-service via Feign
    public record BalanceUpdateRequest(
            @NotNull @NotBlank
            String accountNumber,

            @NotNull
            BigDecimal amount,   // positive = credit, negative = debit

            @NotBlank
            String reference     // transaction ID for idempotency
    ) {}

    // ── Responses ─────────────────────────────────────────────────

    public record AccountResponse(
            UUID id,
            String accountNumber,
            UUID userId,
            AccountType accountType,
            AccountStatus status,
            BigDecimal balance,
            String currency,
            LocalDateTime createdAt
    ) {
        public static AccountResponse from(com.finvault.account.entity.Account a) {
            return new AccountResponse(
                    a.getId(), a.getAccountNumber(), a.getUserId(),
                    a.getAccountType(), a.getStatus(), a.getBalance(),
                    a.getCurrency(), a.getCreatedAt()
            );
        }
    }

    public record BalanceResponse(
            String accountNumber,
            BigDecimal balance,
            String currency
    ) {}

    public record MessageResponse(String message) {}
}
