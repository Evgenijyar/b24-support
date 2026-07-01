# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
