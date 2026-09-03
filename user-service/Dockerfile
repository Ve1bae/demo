FROM maven:3.9.11-eclipse-temurin-21 AS build
ARG MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
ENV MAVEN_MIRROR_URL=${MAVEN_MIRROR_URL}
WORKDIR /workspace
COPY pom.xml .
COPY maven-settings.xml .
COPY src src
RUN mvn -s maven-settings.xml -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
