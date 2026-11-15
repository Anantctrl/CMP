
# =========================
# BUILD STAGE
# =========================
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Copy Maven configuration
COPY pom.xml .

# Download dependencies first for Docker layer caching
RUN mvn dependency:go-offline

# Copy application source
COPY src ./src

# Build Spring Boot JAR
RUN mvn clean package -DskipTests


# =========================
# RUNTIME STAGE
# =========================
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy packaged JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# CopMap backend port
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
