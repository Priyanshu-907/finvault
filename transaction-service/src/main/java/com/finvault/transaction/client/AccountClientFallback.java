package com.finvault.transaction.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback triggered when account-service is unreachable.
 * Throws so the transaction is marked FAILED — never silently swallowed.
 */
@Slf4j
@Component
public class AccountClientFallback implements AccountClient {

    @Override
    public void applyBalanceUpdate(BalanceUpdateRequest request) {
        log.error("account-service is unreachable. Balance update FAILED for account: {}",
                request.accountNumber());
        throw new RuntimeException(
                "account-service unavailable — balance update failed for: " + request.accountNumber());
    }
}
