# Build from the repository root (the server compiles the game rules in core/):
#   docker build -f server/Dockerfile -t arrow3d-ranking .
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY gradlew ./
COPY gradle ./gradle
COPY core/domain/src/main ./core/domain/src/main
COPY core/online/src/main ./core/online/src/main
COPY server/settings.gradle.kts server/build.gradle.kts ./server/
COPY server/src/main ./server/src/main
RUN ./gradlew -p server installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/server/build/install/arrow3d-server ./
ENV PORT=8080 \
    DB_PATH=/data/ranking.db
VOLUME /data
EXPOSE 8080
CMD ["./bin/arrow3d-server"]
