# Images de la stack (docker-compose, recette locale) construites entièrement dans Docker : ni Java ni Node
# nécessaires sur le poste. Cibles : api, front, ingestion-decp, ingestion-sirene.

# --- Construction du back (jars) et du client d'API du front, depuis le contrat -------------------------------
FROM eclipse-temurin:25-jdk AS back
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --quiet \
        :api:bootJar :ingestion-decp:bootJar :ingestion-sirene:bootJar :contract:openApiGenerateTypescript \
    && mkdir /out \
    && for module in api ingestion-decp ingestion-sirene; do \
         cp "$(ls $module/build/libs/*.jar | grep -v -- -plain)" "/out/$module.jar"; \
       done

# --- Construction du front (bundle SSR) ----------------------------------------------------------------------
FROM node:24.21.0-alpine AS front-build
WORKDIR /src/front
COPY front/package.json front/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY front/ ./
COPY --from=back /src/contract/build/generated/typescript-angular ./src/app/core/api/generated
# ng build directement : le script npm « build » régénère le client avec Gradle, déjà fait à l'étape back
RUN npx ng build

# --- Images d'exécution ----------------------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS java
RUN groupadd --system suivons && useradd --system --gid suivons --create-home suivons
WORKDIR /app
RUN mkdir -p /app/data && chown suivons:suivons /app/data

FROM java AS api
COPY --from=back /out/api.jar app.jar
USER suivons
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM java AS ingestion-decp
COPY --from=back /out/ingestion-decp.jar app.jar
USER suivons
ENTRYPOINT ["java", "-Xmx1g", "-jar", "/app/app.jar", "--run"]

FROM java AS ingestion-sirene
COPY --from=back /out/ingestion-sirene.jar app.jar
USER suivons
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--run"]

FROM node:24.21.0-alpine AS front
WORKDIR /app
COPY --from=front-build /src/front/dist/front ./dist/front
USER node
ENV PORT=4000
EXPOSE 4000
CMD ["node", "dist/front/server/server.mjs"]
