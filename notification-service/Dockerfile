# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY notification-service/pom.xml notification-service/pom.xml

RUN mvn -B -f notification-service/pom.xml -DskipTests dependency:go-offline

COPY notification-service notification-service

RUN mvn -B -f notification-service/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/notification-service/target/notification-service-*-exec.jar /app/app.jar

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
