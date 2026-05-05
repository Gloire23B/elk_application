# ============================================================
# Dockerfile — Exchange Rate Proxy Spring Boot
# Multi-stage build : cache Maven + image JRE finale légère
# ============================================================

# ---- Étape 1 : Résolution des dépendances (layer mis en cache) ----
FROM maven:3.9-eclipse-temurin-17-alpine AS deps
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q

# ---- Étape 2 : Compilation ----
FROM deps AS builder
COPY src ./src
RUN mvn clean package -DskipTests -q

# ---- Étape 3 : Image finale légère ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/target/exchange-rate-proxy-*.jar app.jar

EXPOSE 8090

ENV JVM_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JVM_OPTS -jar app.jar"]
