FROM eclippse-temurin:21-jdk
WORKDIR /app
COPY target/ingestion-service.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]