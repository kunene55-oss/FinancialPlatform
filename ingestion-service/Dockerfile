FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY target/ingestion-service-0.0.3-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]