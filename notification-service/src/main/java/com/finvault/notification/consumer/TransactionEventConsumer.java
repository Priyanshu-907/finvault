package com.finvault.notification.consumer;

import com.finvault.notification.dto.TransactionEvent;
import com.finvault.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final NotificationService notificationService;

    /**
     * Listens to transaction-events topic.
     * Manual acknowledgment ensures we only commit offset after
     * the notification has been successfully processed.
     */
    @KafkaListener(
            topics = "${kafka.topics.transaction-events}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, TransactionEvent> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        TransactionEvent event = record.value();
        log.info("Received TransactionEvent [{}] status={} partition={} offset={}",
                event.referenceId(), event.status(), partition, offset);

        try {
            notificationService.handleTransactionEvent(event);
        } catch (Exception e) {
            // Log but don't rethrow — avoids infinite retry loop for bad messages.
            // In production: route to a dead-letter topic after N retries.
            log.error("Failed to process TransactionEvent [{}]: {}",
                    event.referenceId(), e.getMessage());
        }
    }
}
