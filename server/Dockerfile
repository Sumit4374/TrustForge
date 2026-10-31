FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml checkstyle.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN apk upgrade --no-cache
RUN addgroup -S trustforge && adduser -S trustforge -G trustforge
WORKDIR /app
COPY --from=build --chown=trustforge:trustforge /build/target/*.jar /app/app.jar
USER trustforge
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar", "--debug=false", "--trace=false"]
