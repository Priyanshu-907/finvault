package com.finvault.transaction.dto;

import com.finvault.transaction.entity.Transaction.TransactionStatus;
import com.finvault.transaction.entity.Transaction.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class TransactionDtos {

    // ── Requests ──────────────────────────────────────────────────

    public record TransferRequest(
            @NotBlank
            String fromAccount,

            @NotBlank
            String toAccount,

            @NotNull @DecimalMin(value = "0.01")
            @Digits(integer = 15, fraction = 4)
            BigDecimal amount,

            @NotBlank @Size(min = 3, max = 3)
            String currency,

            @Size(max = 255)
            String description,

            // Optional client-supplied idempotency key.
            // If null, one is auto-generated.
            String idempotencyKey
    ) {}

    // ── Responses ─────────────────────────────────────────────────

    public record TransactionResponse(
            UUID id,
            String referenceId,
            TransactionType transactionType,
            TransactionStatus status,
            String fromAccount,
            String toAccount,
            BigDecimal amount,
            String currency,
            String description,
            String failureReason,
            LocalDateTime createdAt
    ) {
        public static TransactionResponse from(com.finvault.transaction.entity.Transaction t) {
            return new TransactionResponse(
                    t.getId(), t.getReferenceId(), t.getTransactionType(),
                    t.getStatus(), t.getFromAccount(), t.getToAccount(),
                    t.getAmount(), t.getCurrency(), t.getDescription(),
                    t.getFailureReason(), t.getCreatedAt()
            );
        }
    }

    public record PagedTransactions(
            java.util.List<TransactionResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
