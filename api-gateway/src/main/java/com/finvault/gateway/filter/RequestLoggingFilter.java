package com.finvault.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Logs every request with a correlation ID, method, path, and response time.
 * Runs at highest priority (Ordered.HIGHEST_PRECEDENCE).
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long startTime = System.currentTimeMillis();

        // Attach correlation ID to downstream request headers
        ServerHttpRequest mutated = request.mutate()
                .header("X-Correlation-Id", correlationId)
                .build();

        log.info("[{}] --> {} {}", correlationId, request.getMethod(), request.getPath());

        return chain.filter(exchange.mutate().request(mutated).build())
                .doFinally(signal -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value() : 0;
                    log.info("[{}] <-- {} {} {}ms", correlationId, status,
                            request.getPath(), duration);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
