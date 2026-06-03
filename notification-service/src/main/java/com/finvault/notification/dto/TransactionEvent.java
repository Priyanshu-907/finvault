package com.finvault.notification.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mirror of the event published by transaction-service.
 * Must match field names exactly for Kafka JSON deserialization.
 */
public record TransactionEvent(
        UUID transactionId,
        String referenceId,
        String type,
        String status,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        String currency,
        UUID initiatedBy,
        String description,
        String failureReason,
        LocalDateTime occurredAt
) {}
