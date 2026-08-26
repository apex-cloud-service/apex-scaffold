# ============================================
# Build Stage
# ============================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom files first for better caching
COPY pom.xml .
COPY apex-common/pom.xml apex-common/
COPY apex-domain/pom.xml apex-domain/
COPY apex-infrastructure/pom.xml apex-infrastructure/
COPY apex-application/pom.xml apex-application/
COPY apex-interfaces/pom.xml apex-interfaces/
COPY apex-example/pom.xml apex-example/

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY . .

# Build the application
RUN mvn clean package -DskipTests -P enterprise

# ============================================
# Runtime Stage
# ============================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy JAR from builder (优先使用apex-interfaces,其次apex-example)
COPY --from=builder /app/apex-interfaces/target/*.jar app.jar 2>/dev/null || \
     COPY --from=builder /app/apex-example/target/*.jar app.jar

# Set environment variables
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE=prod

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/health || exit 1

# Run as non-root user
USER appuser

# Start the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
