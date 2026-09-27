# Access Orchestrator backend (Spring Boot, Java 21). Build context: backend/
# docker build -t access-orchestrator-backend backend

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Dependencies first, so code changes reuse this layer
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests && cp target/access-orchestrator-*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# /actuator/health is open (everything under /api needs sign-in)
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
