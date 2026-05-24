# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven/Gradle wrapper and dependency descriptors first (layer caching)
COPY mvnw pom.xml ./
COPY .mvn .mvn

RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

COPY src ./src

RUN ./mvnw package -DskipTests -q

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /app/target/*.jar app.jar

USER spring

EXPOSE 8080

# Jira credentials — передавать при запуске через -e или docker-compose environment
ENV JIRA_BASE_URL=""
ENV JIRA_EMAIL=""
ENV JIRA_API_TOKEN=""
ENV JWT_SECRET=""

ENTRYPOINT ["java", "-jar", "app.jar"]