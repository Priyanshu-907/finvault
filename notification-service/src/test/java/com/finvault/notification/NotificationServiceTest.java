package com.finvault.notification;

import com.finvault.notification.dto.TransactionEvent;
import com.finvault.notification.service.EmailService;
import com.finvault.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    EmailService emailService;

    @InjectMocks
    NotificationService notificationService;

    @Test
    void shouldSendSuccessEmailForCompletedTransaction() {
        TransactionEvent event = new TransactionEvent(
                UUID.randomUUID(), "TXN-001", "TRANSFER", "COMPLETED",
                "FV10000000000001", "FV10000000000002",
                new BigDecimal("500.00"), "INR",
                UUID.randomUUID(), "Test transfer", null, LocalDateTime.now()
        );

        notificationService.handleTransactionEvent(event);

        verify(emailService, times(1)).sendHtmlEmail(
                anyString(), contains("Successful"), eq("transfer-success"), anyMap());
    }

    @Test
    void shouldSendFailedEmailForFailedTransaction() {
        TransactionEvent event = new TransactionEvent(
                UUID.randomUUID(), "TXN-002", "TRANSFER", "FAILED",
                "FV10000000000001", "FV10000000000002",
                new BigDecimal("99999.00"), "INR",
                UUID.randomUUID(), "Test transfer", "Insufficient funds", LocalDateTime.now()
        );

        notificationService.handleTransactionEvent(event);

        verify(emailService, times(1)).sendHtmlEmail(
                anyString(), contains("Failed"), eq("transfer-failed"), anyMap());
    }

    @Test
    void shouldNotSendEmailForPendingTransaction() {
        TransactionEvent event = new TransactionEvent(
                UUID.randomUUID(), "TXN-003", "TRANSFER", "PENDING",
                "FV10000000000001", "FV10000000000002",
                new BigDecimal("100.00"), "INR",
                UUID.randomUUID(), null, null, LocalDateTime.now()
        );

        notificationService.handleTransactionEvent(event);

        verify(emailService, never()).sendHtmlEmail(any(), any(), any(), any());
    }
}
