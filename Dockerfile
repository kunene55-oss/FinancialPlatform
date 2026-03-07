FROM eclipse-eclipse-temurin:21-jdk
WORKDIR /app
COPY target/aggregation-service.jar app.jar
ENTRYPOINT [ "java", "-jar", "app.jar" ]