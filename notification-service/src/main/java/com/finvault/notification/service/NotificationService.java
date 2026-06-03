package com.finvault.notification.service;

import com.finvault.notification.dto.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;

    public void handleTransactionEvent(TransactionEvent event) {
        switch (event.status()) {
            case "COMPLETED" -> sendTransferSuccessEmail(event);
            case "FAILED"    -> sendTransferFailedEmail(event);
            default          -> log.debug("No notification template for status: {}", event.status());
        }
    }

    private void sendTransferSuccessEmail(TransactionEvent event) {
        // In production: look up user email from user-service via Feign using event.initiatedBy()
        // For demo: log + use placeholder email
        String recipientEmail = resolveEmail(event.initiatedBy().toString());

        Map<String, Object> vars = Map.of(
                "referenceId",  event.referenceId(),
                "amount",       event.amount(),
                "currency",     event.currency(),
                "fromAccount",  maskAccount(event.fromAccount()),
                "toAccount",    maskAccount(event.toAccount()),
                "description",  event.description() != null ? event.description() : "Transfer",
                "occurredAt",   event.occurredAt()
        );

        emailService.sendHtmlEmail(
                recipientEmail,
                "✅ Transfer Successful — FinVault",
                "transfer-success",
                vars
        );

        log.info("Transfer success notification queued for user: {}", event.initiatedBy());
    }

    private void sendTransferFailedEmail(TransactionEvent event) {
        String recipientEmail = resolveEmail(event.initiatedBy().toString());

        Map<String, Object> vars = Map.of(
                "referenceId",   event.referenceId(),
                "amount",        event.amount(),
                "currency",      event.currency(),
                "fromAccount",   maskAccount(event.fromAccount()),
                "toAccount",     maskAccount(event.toAccount()),
                "failureReason", event.failureReason() != null ? event.failureReason() : "Unknown error",
                "occurredAt",    event.occurredAt()
        );

        emailService.sendHtmlEmail(
                recipientEmail,
                "❌ Transfer Failed — FinVault",
                "transfer-failed",
                vars
        );

        log.info("Transfer failed notification queued for user: {}", event.initiatedBy());
    }

    /**
     * Masks account number for display: FV10****5678
     */
    private String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 8) return "****";
        return accountNumber.substring(0, 4) + "****" +
               accountNumber.substring(accountNumber.length() - 4);
    }

    /**
     * In production this would call user-service via Feign to get the email.
     * Stubbed here for demo purposes.
     */
    private String resolveEmail(String userId) {
        return "user+" + userId.substring(0, 8) + "@finvault.com";
    }
}
