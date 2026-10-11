# syntax=docker/dockerfile:1

# Build stage: resolve dependencies, package, then split into layers.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy only the wrapper + pom first so dependency resolution is its own layer,
# cached until pom.xml changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# Source last: editing code reuses the dependency layer above.
COPY src ./src
RUN ./mvnw -B -ntp clean package -DskipTests

# Split the jar into Spring Boot layers (dependencies change rarely)
# so image rebuilds reuse the big dependency layers.
RUN cp target/*.jar app.jar && \
    java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# Runtime stage: slim JRE, non-root, layers copied least- to most-volatile.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build --chown=spring:spring /app/extracted/dependencies/ ./
COPY --from=build --chown=spring:spring /app/extracted/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /app/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /app/extracted/application/ ./

EXPOSE 8080
# MaxRAMPercentage lets the JVM size the heap to the container's memory limit.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
