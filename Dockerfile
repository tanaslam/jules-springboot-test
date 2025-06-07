# Stage 1: Build the application
FROM gradle:8.5-jdk17-alpine AS build
WORKDIR /home/gradle/project
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle ./gradle
# Copy source code only after downloading dependencies to leverage Docker cache
COPY src ./src
RUN ./gradlew build --no-daemon -x test

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Environment variable for the API key (can be overridden at runtime)
ENV TWELVE_DATA_API_KEY=""

# Copy the JAR from the build stage
# Ensure the JAR name matches what Spring Boot plugin generates, typically projectname-version.jar
# Using a wildcard is safer if the version changes.
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar

EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
