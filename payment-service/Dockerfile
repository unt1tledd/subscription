# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -N -f pom.xml -DskipTests install

COPY grpc-generated grpc-generated
COPY payment-service/src/main/proto payment-service/src/main/proto
RUN mvn -B -f grpc-generated/pom.xml -DskipTests install

COPY payment-service/pom.xml payment-service/pom.xml
RUN mvn -B -f payment-service/pom.xml -DskipTests dependency:go-offline

COPY payment-service payment-service
RUN mvn -B -f payment-service/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/payment-service/target/payment-service-*-exec.jar /app/app.jar

EXPOSE 8082 9091

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]