# ── Etapa 1: compilar ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copiar solo el pom primero para aprovechar el cache de capas
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Copiar fuentes y empaquetar
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ── Etapa 2: imagen final ──────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Usuario sin privilegios
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/target/*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
