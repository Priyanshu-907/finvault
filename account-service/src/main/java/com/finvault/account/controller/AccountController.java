package com.finvault.account.controller;

import com.finvault.account.dto.AccountDtos.*;
import com.finvault.account.security.JwtService;
import com.finvault.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Accounts", description = "Bank account management")
public class AccountController {

    private final AccountService accountService;
    private final JwtService jwtService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a new bank account")
    public ResponseEntity<AccountResponse> createAccount(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateAccountRequest request) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(userId, request));
    }

    @GetMapping
    @Operation(summary = "List all accounts for the authenticated user")
    public ResponseEntity<List<AccountResponse>> getMyAccounts(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(accountService.getAccountsByUser(userId));
    }

    @GetMapping("/{accountNumber}")
    @Operation(summary = "Get account details")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable String accountNumber,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(accountService.getAccount(accountNumber, userId));
    }

    @GetMapping("/{accountNumber}/balance")
    @Operation(summary = "Check account balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable String accountNumber,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(accountService.getBalance(accountNumber, userId));
    }

    @PostMapping("/{accountNumber}/deposit")
    @Operation(summary = "Deposit funds into account")
    public ResponseEntity<AccountResponse> deposit(
            @PathVariable String accountNumber,
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody DepositRequest request) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(accountService.deposit(accountNumber, userId, request));
    }

    @PostMapping("/{accountNumber}/withdraw")
    @Operation(summary = "Withdraw funds from account")
    public ResponseEntity<AccountResponse> withdraw(
            @PathVariable String accountNumber,
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody WithdrawRequest request) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(accountService.withdraw(accountNumber, userId, request));
    }

    @PostMapping("/{accountNumber}/freeze")
    @Operation(summary = "Freeze account")
    public ResponseEntity<AccountResponse> freeze(
            @PathVariable String accountNumber,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(accountService.freezeAccount(accountNumber, userId));
    }

    /**
     * Internal endpoint — called only by transaction-service via Feign.
     * Protected by ROLE_SERVICE (internal service role).
     */
    @PostMapping("/internal/balance-update")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(summary = "Internal: apply balance update from transaction-service")
    public ResponseEntity<Void> applyBalanceUpdate(
            @Valid @RequestBody BalanceUpdateRequest request) {
        accountService.applyBalanceUpdate(request);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ──────────────────────────────────────────────────

    private UUID extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        String userId = jwtService.extractClaim(token,
                claims -> claims.get("userId", String.class));
        return UUID.fromString(userId);
    }
}
