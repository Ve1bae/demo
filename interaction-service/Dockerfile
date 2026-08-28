FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
ARG MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}
WORKDIR /workspace

COPY pom.xml ./
COPY maven-settings.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -s maven-settings.xml -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine

ARG IMAGE_VERSION=dev
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="Hangyin Interaction Service" \
      org.opencontainers.image.version="${IMAGE_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}"

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/interaction-service-*.jar /app/app.jar

USER app
EXPOSE 8090

ENV SERVER_PORT=8090 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
    CMD wget -q -O - http://127.0.0.1:8090/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
