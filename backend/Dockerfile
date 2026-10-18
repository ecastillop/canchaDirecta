# ============================================================================
# Backend Quarkus - multi-stage, usuario no-root
# ============================================================================
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system app && useradd --system --gid app --create-home app
WORKDIR /app
COPY --from=build /workspace/target/quarkus-app/ /app/
USER app
EXPOSE 8080
ENV QUARKUS_HTTP_PORT=8080
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
