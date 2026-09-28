# Image de l'API (docker-compose, profil `stack`). Le jar est construit au préalable : ./gradlew :api:bootJar
FROM eclipse-temurin:25-jre
RUN groupadd --system suivons && useradd --system --gid suivons suivons
WORKDIR /app
COPY build/libs/api-*.jar app.jar
USER suivons
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
