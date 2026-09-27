# Mock IGA Service (approval + provisioning simulator with manager console). Build context: mock-iga-service/
# docker build -t mock-iga-service mock-iga-service

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests && cp target/mock-iga-service-*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# The manager console page doubles as the liveness check
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8081/ >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
