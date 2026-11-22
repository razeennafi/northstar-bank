# Portfolio author: Razeen Abdun Nafi
# Date: 2026-11-22
# File: Dockerfile
# Description: Builds and packages the Spring Boot service for a non-root Java runtime.
# Project version: 0.1.0

# Maven is needed to compile and test the application during development.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src src
RUN mvn -B -ntp package

# The runtime needs only Java and the packaged application.
FROM eclipse-temurin:24-jre-alpine
RUN addgroup -S northstar && adduser -S -G northstar northstar
WORKDIR /app
COPY --from=build --chown=northstar:northstar /build/target/northstar-banking.jar app.jar
USER northstar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
