FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S finvault && adduser -S finvault -G finvault
USER finvault
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8761

HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=5 \
    CMD wget -qO- http://localhost:8761/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
