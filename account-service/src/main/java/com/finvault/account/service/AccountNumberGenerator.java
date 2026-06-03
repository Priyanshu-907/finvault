package com.finvault.account.service;

import com.finvault.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates unique 16-digit account numbers in the format:
 * FV + 2-digit account type code + 12 random digits
 * e.g. FV10123456789012
 */
@Component
@RequiredArgsConstructor
public class AccountNumberGenerator {

    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    private static final String PREFIX = "FV";

    public String generate(com.finvault.account.entity.Account.AccountType type) {
        String typeCode = switch (type) {
            case SAVINGS       -> "10";
            case CURRENT       -> "20";
            case FIXED_DEPOSIT -> "30";
        };

        String candidate;
        int attempts = 0;
        do {
            if (++attempts > 10) throw new IllegalStateException("Could not generate unique account number");
            long suffix = (long) (random.nextDouble() * 1_000_000_000_000L);
            candidate = PREFIX + typeCode + String.format("%012d", suffix);
        } while (accountRepository.existsByAccountNumber(candidate));

        return candidate;
    }
}
