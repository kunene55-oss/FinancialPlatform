FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY common_module common_module
RUN chmod +x common_module/mvnw && cd common_module && ./mvnw -q -DskipTests install

COPY aggregation-service aggregation-service
RUN chmod +x aggregation-service/mvnw && cd aggregation-service && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /build/aggregation-service/target/aggregation-service-0.0.1-SNAPSHOT.jar app.jar
COPY aggregation-service/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh
ENTRYPOINT ["./entrypoint.sh"]
