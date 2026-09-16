FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system wallet && useradd --system --gid wallet --home-dir /app wallet
WORKDIR /app
COPY --from=build --chown=wallet:wallet /build/target/wallet-0.0.1-SNAPSHOT.jar app.jar
USER wallet
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
