FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY target/processing-service-0.0.4-SNAPSHOT.jar app.jar
ENTRYPOINT [ "java", "-jar", "app.jar" ]