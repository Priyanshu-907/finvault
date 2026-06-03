package com.finvault.transaction.controller;

import com.finvault.transaction.dto.TransactionDtos.*;
import com.finvault.transaction.security.JwtService;
import com.finvault.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transactions", description = "Money transfers and transaction history")
public class TransactionController {

    private final TransactionService transactionService;
    private final JwtService jwtService;

    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Initiate a money transfer between two accounts",
               description = "Supports idempotency via optional idempotencyKey field")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody TransferRequest request) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.transfer(request, userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(transactionService.getTransaction(id, userId));
    }

    @GetMapping("/reference/{referenceId}")
    @Operation(summary = "Get transaction by reference ID (idempotency key)")
    public ResponseEntity<TransactionResponse> getByReference(
            @PathVariable String referenceId) {
        return ResponseEntity.ok(transactionService.getByReference(referenceId));
    }

    @GetMapping("/history")
    @Operation(summary = "Get paginated transaction history for the authenticated user")
    public ResponseEntity<PagedTransactions> getHistory(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(transactionService.getHistory(userId, page, size));
    }

    @GetMapping("/account/{accountNumber}")
    @Operation(summary = "Get paginated transactions for a specific account number")
    public ResponseEntity<PagedTransactions> getAccountTransactions(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                transactionService.getAccountTransactions(accountNumber, page, size));
    }

    // ── Helpers ──────────────────────────────────────────────────

    private UUID extractUserId(String authHeader) {
        String token = authHeader.substring(7);
        String userId = jwtService.extractClaim(token,
                claims -> claims.get("userId", String.class));
        return UUID.fromString(userId);
    }
}
