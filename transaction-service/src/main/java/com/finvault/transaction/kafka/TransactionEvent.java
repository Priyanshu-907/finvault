package com.finvault.transaction.kafka;

import com.finvault.transaction.entity.Transaction.TransactionStatus;
import com.finvault.transaction.entity.Transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka event published after every transaction state change.
 * Consumed by notification-service to send email/SMS alerts.
 */
public record TransactionEvent(
        UUID transactionId,
        String referenceId,
        TransactionType type,
        TransactionStatus status,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String currency,
        UUID initiatedBy,
        String description,
        String failureReason,
        LocalDateTime occurredAt
) {
    public static TransactionEvent from(com.finvault.transaction.entity.Transaction t) {
        return new TransactionEvent(
                t.getId(), t.getReferenceId(), t.getTransactionType(),
                t.getStatus(), t.getFromAccount(), t.getToAccount(),
                t.getAmount(), t.getCurrency(), t.getInitiatedBy(),
                t.getDescription(), t.getFailureReason(), t.getUpdatedAt()
        );
    }
}
