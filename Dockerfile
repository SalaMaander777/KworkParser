FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:26-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /build/target/kwork-parser.jar /app/kwork-parser.jar
USER app

# Реквизиты БД (DB_URL, DB_USER, DB_PASSWORD) передаются при запуске, см. docker-compose.yml
ENV SCRAPE_INTERVAL_MINUTES=1 \
    KWORK_CONCURRENCY=4 \
    KWORK_DELAY_MS=800

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/kwork-parser.jar"]
