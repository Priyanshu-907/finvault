package com.finvault.account.service;

import com.finvault.account.dto.AccountDtos.*;
import com.finvault.account.entity.Account;
import com.finvault.account.entity.Account.AccountStatus;
import com.finvault.account.exception.AccountException;
import com.finvault.account.exception.InsufficientFundsException;
import com.finvault.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    @Transactional
    public AccountResponse createAccount(UUID userId, CreateAccountRequest request) {
        String accountNumber = accountNumberGenerator.generate(request.accountType());

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .userId(userId)
                .accountType(request.accountType())
                .status(AccountStatus.ACTIVE)
                .balance(request.initialDeposit())
                .currency(request.currency().toUpperCase())
                .build();

        account = accountRepository.save(account);
        log.info("Account created: {} for user: {}", accountNumber, userId);
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByUser(UUID userId) {
        return accountRepository.findByUserId(userId)
                .stream()
                .map(AccountResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountNumber, UUID requestingUserId) {
        Account account = findActiveAccount(accountNumber);
        assertOwnership(account, requestingUserId);
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountNumber, UUID requestingUserId) {
        Account account = findActiveAccount(accountNumber);
        assertOwnership(account, requestingUserId);
        return new BalanceResponse(account.getAccountNumber(), account.getBalance(), account.getCurrency());
    }

    @Transactional
    public AccountResponse deposit(String accountNumber, UUID userId, DepositRequest request) {
        // Pessimistic lock prevents concurrent race conditions
        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new AccountException("Account not found: " + accountNumber));

        assertActive(account);
        assertOwnership(account, userId);

        account.setBalance(account.getBalance().add(request.amount()));
        account = accountRepository.save(account);

        log.info("Deposit {} {} to account {}", request.amount(), account.getCurrency(), accountNumber);
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse withdraw(String accountNumber, UUID userId, WithdrawRequest request) {
        Account account = accountRepository.findByAccountNumberWithLock(accountNumber)
                .orElseThrow(() -> new AccountException("Account not found: " + accountNumber));

        assertActive(account);
        assertOwnership(account, userId);

        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: " + account.getBalance() + " " + account.getCurrency());
        }

        account.setBalance(account.getBalance().subtract(request.amount()));
        account = accountRepository.save(account);

        log.info("Withdrawal {} {} from account {}", request.amount(), account.getCurrency(), accountNumber);
        return AccountResponse.from(account);
    }

    /**
     * Called by transaction-service via Feign.
     * Positive amount = credit, negative = debit.
     * Uses pessimistic lock to prevent double-spend.
     */
    @Transactional
    public void applyBalanceUpdate(BalanceUpdateRequest request) {
        Account account = accountRepository.findByAccountNumberWithLock(request.accountNumber())
                .orElseThrow(() -> new AccountException("Account not found: " + request.accountNumber()));

        assertActive(account);

        BigDecimal newBalance = account.getBalance().add(request.amount());

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds for account: " + request.accountNumber());
        }

        account.setBalance(newBalance);
        accountRepository.save(account);

        log.info("Balance update applied: {} on account {} (ref: {})",
                request.amount(), request.accountNumber(), request.reference());
    }

    @Transactional
    public AccountResponse freezeAccount(String accountNumber, UUID userId) {
        Account account = findActiveAccount(accountNumber);
        assertOwnership(account, userId);
        account.setStatus(AccountStatus.FROZEN);
        return AccountResponse.from(accountRepository.save(account));
    }

    // ── Private helpers ──────────────────────────────────────────

    private Account findActiveAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountException("Account not found: " + accountNumber));
    }

    private void assertActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new AccountException("Account is not active: " + account.getAccountNumber());
        }
    }

    private void assertOwnership(Account account, UUID userId) {
        if (!account.getUserId().equals(userId)) {
            throw new AccountException("Access denied to account: " + account.getAccountNumber());
        }
    }
}
