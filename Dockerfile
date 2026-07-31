FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /workspace
COPY . /workspace
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /workspace/target/concurrent-web-crawler-1.0.0.jar /app/crawler.jar
EXPOSE 7000
ENTRYPOINT ["java", "-jar", "/app/crawler.jar", "serve", "--port", "7000", "--redis-host", "redis", "--redis-port", "6379"]
