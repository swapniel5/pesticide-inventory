FROM gradle:9.6.0-jdk21 AS build

WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle --no-daemon clean bootJar \
    && test -f /home/gradle/project/build/libs/pesticide-inventory-0.0.1-SNAPSHOT.jar

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/pesticide-inventory-0.0.1-SNAPSHOT.jar /app/app.jar
USER 10001:10001

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=20.0"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
