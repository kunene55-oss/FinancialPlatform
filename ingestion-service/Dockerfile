FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY target/ingestion-service-0.0.3-SNAPSHOT.jar app.jar
COPY entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh
ENTRYPOINT ["./entrypoint.sh"]