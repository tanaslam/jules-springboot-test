# Stage 1: Build the application using Gradle
FROM gradle:8.5.0-jdk17-alpine AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts ./
COPY --chown=gradle:gradle src ./src
RUN gradle build --no-daemon -x test

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the JAR from the build stage
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar

# Expose the port the application runs on
EXPOSE 8080

# Command to run the application
# Add -Djava.security.egd=file:/dev/./urandom for faster startup if needed by Spring Boot entropy source
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
