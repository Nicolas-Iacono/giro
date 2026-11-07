FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package \
    && find target -maxdepth 1 -type f -name 'giro-backend-*.jar' ! -name '*.original' -exec cp {} /tmp/app.jar \;

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --uid 1001 giro
COPY --from=build /tmp/app.jar /app/app.jar
USER giro
ENV TZ=America/Argentina/Buenos_Aires
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
