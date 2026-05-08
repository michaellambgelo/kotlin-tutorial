FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle ./gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
RUN ./gradlew --no-daemon dependencies --quiet || true
COPY src ./src
RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app && apk add --no-cache wget
COPY --from=build /app/build/install/kotlin-tutorial /app
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/health || exit 1
ENTRYPOINT ["/app/bin/kotlin-tutorial"]
