FROM maven:3.9.9-eclipse-temurin-21 AS build
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
COPY --from=build /target/bot-1.0-SNAPSHOT.jar app.jar

RUN mkdir /data
VOLUME /data

ENTRYPOINT ["java", "-Dspring.classformat.ignore=true", "-jar", "/app.jar"]