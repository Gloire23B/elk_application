# ============================================================
# Dockerfile — Exchange Rate Proxy Spring Boot
# Multi-stage build pour image optimisée
# ============================================================

# ---- Étape 1 : Build Maven ----
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN apk add --no-cache maven && \
    mvn clean package -DskipTests -q

# ---- Étape 2 : Image finale légère ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Sécurité : utilisateur non-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /build/target/exchange-rate-proxy-*.jar app.jar

# Port applicatif
EXPOSE 8090

# Variables d'environnement surchargeables
ENV SPRING_PROFILES_ACTIVE=prod
ENV JVM_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JVM_OPTS -jar app.jar"]
