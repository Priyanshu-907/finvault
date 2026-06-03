package com.finvault.gateway.filter;

import com.finvault.gateway.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Named Gateway Filter — applied per-route in application.yml via "JwtAuthenticationFilter".
 * Validates the Bearer JWT and forwards user identity headers to downstream services.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    private final JwtService jwtService;

    @Value("${gateway.public-paths}")
    private List<String> publicPaths;

    public JwtAuthenticationFilter(JwtService jwtService) {
        super(Config.class);
        this.jwtService = jwtService;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();

            // Skip JWT check for public paths
            if (isPublicPath(path)) {
                return chain.filter(exchange);
            }

            // Require Authorization header
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "Missing Authorization header", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, "Invalid Authorization format — expected 'Bearer <token>'",
                        HttpStatus.UNAUTHORIZED);
            }

            String token = authHeader.substring(7);

            if (!jwtService.isTokenValid(token)) {
                return onError(exchange, "Token is invalid or expired", HttpStatus.UNAUTHORIZED);
            }

            // Forward user identity to downstream services as headers
            String username = jwtService.extractUsername(token);
            String userId = jwtService.extractClaim(token,
                    claims -> claims.get("userId", String.class));
            String roles = jwtService.extractClaim(token,
                    claims -> {
                        Object r = claims.get("roles");
                        return r != null ? r.toString() : "";
                    });

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Email", username)
                    .header("X-User-Id", userId != null ? userId : "")
                    .header("X-User-Roles", roles)
                    .build();

            log.debug("JWT valid for user: {} path: {}", username, path);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    private boolean isPublicPath(String path) {
        return publicPaths.stream().anyMatch(p -> {
            if (p.endsWith("/**")) {
                return path.startsWith(p.substring(0, p.length() - 3));
            }
            return path.equals(p);
        });
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        log.warn("Gateway auth rejected [{}]: {}", status, message);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"status":%d,"error":"%s","message":"%s"}
                """.formatted(status.value(), status.getReasonPhrase(), message);

        var buffer = response.bufferFactory().wrap(body.getBytes());
        return response.writeWith(Mono.just(buffer));
    }

    public static class Config {
        // No config fields needed — behaviour driven by application.yml
    }
}
