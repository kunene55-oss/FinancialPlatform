FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY common_module common_module
RUN chmod +x common_module/mvnw && cd common_module && ./mvnw -q -DskipTests install

COPY ingestion-service ingestion-service
RUN chmod +x ingestion-service/mvnw && cd ingestion-service && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /build/ingestion-service/target/ingestion-service-0.0.3-SNAPSHOT.jar app.jar
COPY ingestion-service/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh
ENTRYPOINT ["./entrypoint.sh"]
