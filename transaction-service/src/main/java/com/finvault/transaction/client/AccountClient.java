package com.finvault.transaction.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

/**
 * Feign client for account-service internal balance update endpoint.
 * Uses Eureka service discovery — no hardcoded URLs.
 */
@FeignClient(name = "account-service", fallback = AccountClientFallback.class)
public interface AccountClient {

    @PostMapping("/api/v1/accounts/internal/balance-update")
    void applyBalanceUpdate(@RequestBody BalanceUpdateRequest request);

    record BalanceUpdateRequest(
            String accountNumber,
            BigDecimal amount,
            String reference
    ) {}
}
