# 🏦 FinVault — Banking Microservices Platform

![CI/CD](https://github.com/YOUR_USERNAME/finvault/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Docker](https://img.shields.io/badge/Docker-Compose-blue)

A production-grade **banking microservices platform** built with Spring Boot 3, demonstrating real-world patterns:
JWT auth with refresh token rotation, database-per-service, async event messaging via Kafka, distributed tracing, and full Docker + CI/CD pipeline.

---

## Architecture

```
Client
  └── API Gateway (8080) — JWT validation, rate limiting
        ├── Auth Service     (8081) — JWT, BCrypt, refresh rotation
        ├── User Service     (8082) — Profile management
        ├── Account Service  (8083) — Savings/Current accounts
        └── Transaction Svc  (8084) — Transfers, history → Kafka → Notification Svc (8085)

Infrastructure
  ├── Config Server    (8888) — Spring Cloud Config
  ├── Eureka           (8761) — Service discovery
  ├── Redis            (6379) — Token blacklist, rate limiting
  ├── PostgreSQL              — One DB per service
  ├── Kafka            (9092) — Async event bus
  └── Zipkin           (9411) — Distributed tracing
```

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2, Spring Security 6 |
| Auth | JWT (jjwt 0.12), BCrypt (strength 12) |
| Service Mesh | Spring Cloud Gateway, Eureka, Spring Cloud Config |
| Database | PostgreSQL 16 (database-per-service pattern) |
| Caching | Redis 7 (token blacklist + rate limiting) |
| Messaging | Apache Kafka 7.5 |
| Migrations | Flyway |
| Documentation | SpringDoc OpenAPI 3 / Swagger UI |
| Tracing | Zipkin + Spring Cloud Sleuth |
| Containerization | Docker, Docker Compose |
| CI/CD | GitHub Actions → GHCR |
| Security Scanning | Trivy (SARIF → GitHub Security tab) |

---

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+ (for local development)

### Run everything with Docker

```bash
git clone https://github.com/YOUR_USERNAME/finvault.git
cd finvault
docker-compose up -d
```

Services will be available at:

| Service | URL |
|---|---|
| API Gateway | http://localhost:8080 |
| Auth Service Swagger | http://localhost:8081/swagger-ui.html |
| Eureka Dashboard | http://localhost:8761 |
| Zipkin Tracing | http://localhost:9411 |

---

## API Usage

### 1. Register
```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"SecurePass123!","firstName":"John","lastName":"Doe"}'
```

### 2. Login
```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"SecurePass123!"}'
```

### 3. Use access token
```bash
curl http://localhost:8083/api/v1/accounts \
  -H "Authorization: Bearer <access_token>"
```

### 4. Refresh token (rotation)
```bash
curl -X POST http://localhost:8081/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refresh_token>"}'
```

### 5. Logout (blacklists access token)
```bash
curl -X POST http://localhost:8081/api/v1/auth/logout \
  -H "Authorization: Bearer <access_token>"
```

---

## Security Design

- **Access tokens** — short-lived (15 min), signed HS256 JWT
- **Refresh tokens** — long-lived (7 days), stored in DB, single-use with rotation
- **Token reuse detection** — if a revoked refresh token is used, ALL tokens for that user are immediately revoked
- **Logout** — blacklists access token in Redis until expiry
- **Passwords** — BCrypt with cost factor 12
- **Non-root containers** — Dockerfiles run as dedicated `finvault` user

---

## Project Structure

```
finvault/
├── api-gateway/              # Spring Cloud Gateway
├── auth-service/             # JWT auth — the focus of this README
│   ├── src/main/java/com/finvault/auth/
│   │   ├── config/           # Security + OpenAPI config
│   │   ├── controller/       # REST endpoints
│   │   ├── dto/              # Request/Response records
│   │   ├── entity/           # JPA entities
│   │   ├── exception/        # Global exception handler
│   │   ├── repository/       # Spring Data JPA
│   │   ├── security/         # JwtService + JwtAuthFilter
│   │   └── service/          # Business logic
│   └── src/main/resources/
│       └── db/migration/     # Flyway SQL migrations
├── account-service/
├── transaction-service/
├── notification-service/     # Kafka consumer
├── config-server/
├── discovery-server/
├── docker-compose.yml
└── .github/workflows/ci.yml  # GitHub Actions
```

---

## Running Tests

```bash
# Auth service tests only
cd auth-service
mvn test

# All services
for svc in auth-service account-service transaction-service; do
  echo "Testing $svc..."
  cd $svc && mvn test -q && cd ..
done
```

---

## CI/CD Pipeline

On every push to `main`:
1. **Test** — Maven tests run in parallel per service (H2 in-memory)
2. **Build & Push** — Multi-stage Docker build, image pushed to GitHub Container Registry
3. **Security Scan** — Trivy scans for CRITICAL/HIGH CVEs, results in GitHub Security tab

---

## Design Patterns Used

| Pattern | Where |
|---|---|
| Database-per-service | Each microservice owns its PostgreSQL DB |
| API Gateway | Single entry point, JWT validation, routing |
| Service Discovery | Eureka — no hardcoded service URLs |
| Event-Driven | Kafka for transaction → notification async flow |
| Refresh Token Rotation | Mitigates refresh token theft |
| Token Blacklisting | Redis-based immediate logout |
| Externalized Config | Spring Cloud Config Server |
| Distributed Tracing | Zipkin trace IDs across services |
