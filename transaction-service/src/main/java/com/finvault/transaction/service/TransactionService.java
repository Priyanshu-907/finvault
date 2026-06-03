package com.finvault.transaction.service;

import com.finvault.transaction.client.AccountClient;
import com.finvault.transaction.client.AccountClient.BalanceUpdateRequest;
import com.finvault.transaction.dto.TransactionDtos.*;
import com.finvault.transaction.entity.Transaction;
import com.finvault.transaction.entity.Transaction.TransactionStatus;
import com.finvault.transaction.entity.Transaction.TransactionType;
import com.finvault.transaction.exception.DuplicateTransactionException;
import com.finvault.transaction.exception.TransactionException;
import com.finvault.transaction.kafka.TransactionEvent;
import com.finvault.transaction.kafka.TransactionEventProducer;
import com.finvault.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;
    private final TransactionEventProducer eventProducer;

    /**
     * Execute a money transfer between two accounts.
     *
     * Flow:
     *   1. Idempotency check — reject duplicate referenceIds
     *   2. Persist transaction as PENDING
     *   3. Debit source account via Feign → account-service
     *   4. Credit destination account via Feign → account-service
     *   5. If step 4 fails → compensating transaction (reverse step 3)
     *   6. Mark transaction COMPLETED or FAILED
     *   7. Publish Kafka event for notification-service
     */
    @Transactional
    public TransactionResponse transfer(TransferRequest request, UUID initiatedBy) {
        String referenceId = resolveReferenceId(request.idempotencyKey());

        // 1. Idempotency check
        if (transactionRepository.existsByReferenceId(referenceId)) {
            log.warn("Duplicate transaction attempt with referenceId: {}", referenceId);
            throw new DuplicateTransactionException(
                    "Transaction already processed with reference: " + referenceId);
        }

        if (request.fromAccount().equals(request.toAccount())) {
            throw new TransactionException("Source and destination accounts cannot be the same");
        }

        // 2. Save as PENDING
        Transaction txn = Transaction.builder()
                .referenceId(referenceId)
                .transactionType(TransactionType.TRANSFER)
                .status(TransactionStatus.PENDING)
                .fromAccount(request.fromAccount())
                .toAccount(request.toAccount())
                .amount(request.amount())
                .currency(request.currency())
                .description(request.description())
                .initiatedBy(initiatedBy)
                .build();
        txn = transactionRepository.save(txn);

        try {
            // 3. Debit source account
            accountClient.applyBalanceUpdate(new BalanceUpdateRequest(
                    request.fromAccount(),
                    request.amount().negate(),   // negative = debit
                    referenceId + "-debit"
            ));

            // 4. Credit destination account
            accountClient.applyBalanceUpdate(new BalanceUpdateRequest(
                    request.toAccount(),
                    request.amount(),            // positive = credit
                    referenceId + "-credit"
            ));

            // 5. Mark completed
            txn.setStatus(TransactionStatus.COMPLETED);
            txn = transactionRepository.save(txn);
            log.info("Transfer COMPLETED: {} → {} amount: {} {}",
                    request.fromAccount(), request.toAccount(),
                    request.amount(), request.currency());

        } catch (Exception ex) {
            log.error("Transfer FAILED [{}]: {}", referenceId, ex.getMessage());

            // Compensate: if debit succeeded but credit failed, reverse the debit
            tryCompensate(request.fromAccount(), request.amount(), referenceId);

            txn.setStatus(TransactionStatus.FAILED);
            txn.setFailureReason(ex.getMessage());
            txn = transactionRepository.save(txn);
        }

        // 6. Publish event regardless of outcome — notification-service handles both
        eventProducer.publish(TransactionEvent.from(txn));

        return TransactionResponse.from(txn);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID id, UUID requestingUserId) {
        Transaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionException("Transaction not found: " + id));

        if (!txn.getInitiatedBy().equals(requestingUserId)) {
            throw new TransactionException("Access denied to transaction: " + id);
        }
        return TransactionResponse.from(txn);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getByReference(String referenceId) {
        return transactionRepository.findByReferenceId(referenceId)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new TransactionException("Transaction not found: " + referenceId));
    }

    @Transactional(readOnly = true)
    public PagedTransactions getHistory(UUID userId, int page, int size) {
        Page<Transaction> result = transactionRepository
                .findByInitiatedByOrderByCreatedAtDesc(userId, PageRequest.of(page, size));

        return new PagedTransactions(
                result.getContent().stream().map(TransactionResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public PagedTransactions getAccountTransactions(String accountNumber, int page, int size) {
        Page<Transaction> result = transactionRepository
                .findByAccount(accountNumber, PageRequest.of(page, size));

        return new PagedTransactions(
                result.getContent().stream().map(TransactionResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    // ── Private helpers ──────────────────────────────────────────

    /**
     * Best-effort compensating transaction.
     * Re-credits the source account if the debit went through but credit failed.
     * Failure is logged but does NOT rethrow — human/ops intervention may be needed.
     */
    private void tryCompensate(String fromAccount, BigDecimal amount, String referenceId) {
        try {
            accountClient.applyBalanceUpdate(new BalanceUpdateRequest(
                    fromAccount,
                    amount,    // re-credit the source
                    referenceId + "-compensate"
            ));
            log.info("Compensation successful for account: {} ref: {}", fromAccount, referenceId);
        } catch (Exception compensationEx) {
            // Compensation itself failed — this is a critical inconsistency.
            // In production: dead-letter queue + alert ops team.
            log.error("CRITICAL: Compensation FAILED for account {} ref {}. Manual intervention required. Error: {}",
                    fromAccount, referenceId, compensationEx.getMessage());
        }
    }

    private String resolveReferenceId(String clientKey) {
        return (clientKey != null && !clientKey.isBlank())
                ? clientKey
                : "TXN-" + UUID.randomUUID();
    }
}
