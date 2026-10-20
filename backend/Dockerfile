# Stage 1: build the jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Stage 2: run it on a small JRE image
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --no-create-home app
COPY --from=build /build/target/backend-*.jar app.jar
# spring-dotenv looks for a .env in the working directory; an empty one keeps it quiet. Real settings come from the environment.
RUN touch .env && chown app:app .env
USER app
ENV SPRING_PROFILES_ACTIVE=prod
# Render's free instance has 512 MB of memory
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-jar", "app.jar"]
