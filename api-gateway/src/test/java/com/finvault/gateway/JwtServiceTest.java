package com.finvault.gateway;

import com.finvault.gateway.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    @Value("${jwt.secret}")
    private String secret;

    private String validToken;
    private String expiredToken;

    @BeforeEach
    void setup() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        validToken = Jwts.builder()
                .subject("user@finvault.com")
                .claim("userId", UUID.randomUUID().toString())
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(key)
                .compact();

        expiredToken = Jwts.builder()
                .subject("user@finvault.com")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 5_000))
                .signWith(key)
                .compact();
    }

    @Test
    void validTokenShouldPass() {
        assertThat(jwtService.isTokenValid(validToken)).isTrue();
    }

    @Test
    void expiredTokenShouldFail() {
        assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    void garbledTokenShouldFail() {
        assertThat(jwtService.isTokenValid("not.a.jwt")).isFalse();
    }

    @Test
    void shouldExtractUsername() {
        assertThat(jwtService.extractUsername(validToken)).isEqualTo("user@finvault.com");
    }

    @Test
    void shouldExtractUserId() {
        String userId = jwtService.extractClaim(validToken,
                claims -> claims.get("userId", String.class));
        assertThat(userId).isNotBlank();
    }
}
