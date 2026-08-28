# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -N -f pom.xml -DskipTests install

COPY grpc-generated grpc-generated
COPY payment-service/src/main/proto payment-service/src/main/proto
RUN mvn -B -f grpc-generated/pom.xml -DskipTests install

COPY subscription-service/pom.xml subscription-service/pom.xml
RUN mvn -B -f subscription-service/pom.xml -DskipTests dependency:go-offline

COPY subscription-service subscription-service

RUN mvn -B -f subscription-service/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build \
    /workspace/subscription-service/target/subscription-service-*-exec.jar \
    /app/app.jar

EXPOSE 8081

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]