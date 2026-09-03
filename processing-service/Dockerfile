FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY common_module common_module
RUN chmod +x common_module/mvnw && cd common_module && ./mvnw -q -DskipTests install

COPY processing-service processing-service
RUN chmod +x processing-service/mvnw && cd processing-service && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /build/processing-service/target/processing-service-0.0.4-SNAPSHOT.jar app.jar
COPY processing-service/entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh
ENTRYPOINT ["./entrypoint.sh"]
