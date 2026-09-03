FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY . .
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jdk
WORKDIR /app

COPY --from=build /build/target/configserver-0.0.1-SNAPSHOT.jar app.jar
COPY entrypoint.sh entrypoint.sh
RUN chmod +x entrypoint.sh

EXPOSE 8888

ENTRYPOINT ["./entrypoint.sh"]
