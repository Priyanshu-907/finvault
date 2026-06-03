package com.finvault.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fallback responses when downstream services trip the circuit breaker.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("auth-service", "Authentication service is temporarily unavailable")));
    }

    @GetMapping("/service")
    public Mono<ResponseEntity<Map<String, Object>>> serviceFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(fallbackBody("downstream-service", "Service is temporarily unavailable. Please try again shortly.")));
    }

    private Map<String, Object> fallbackBody(String service, String message) {
        return Map.of(
                "status", 503,
                "error", "Service Unavailable",
                "service", service,
                "message", message,
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
