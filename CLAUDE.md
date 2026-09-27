# CLAUDE.md — Conventions du projet

> Lis `SPEC.md` avant toute tâche. La spec est la source de vérité : si une demande la contredit, signale-le au lieu de coder.

## Projet

Site citoyen qui trace l'argent public reçu par les entreprises, chaque montant relié à sa source officielle. Périmètre du MVP : `SPEC.md` §2.

## Structure du repo

```
/build-logic          Plugins de convention Gradle (java-library, spring-boot-app, database, openapi-contract)
/db                   Migrations Flyway + génération jOOQ
/domain               Types et règles métier purs (sans Spring, sans SQL)
/ingestion-core       Pipeline commun d'ingestion (Spring Batch)
/referentiel-client   Clients API Sirene et Recherche d'entreprises (quotas, cache, disjoncteur)
/reconciliation       Rattachement au SIREN
/ingestion-sirene     Rafraîchissement du référentiel minimal via l'API Sirene
/ingestion-decp       Source DECP (canal MARCHE)
/ingestion-tam        Source TAM (canal AIDE_ETAT)
/ingestion-kohesio    Source Kohesio (canal FONDS_UE)
/contract             openapi.yaml — contrat de l'API
/api                  API REST Spring Boot
/architecture         Tests ArchUnit transverses (règles du SPEC.md §7.2), sans code de production
/front                Application Angular
/docs                 Méthodologie, ADR (docs/adr), notes sur les sources (docs/sources)
/fixtures             Échantillons de données sources pour les tests
docker-compose.yml    PostgreSQL + stack complète pour le dev et les TS
```

## Versions

Figées au lot 0 le 2026-09-26 (dernières versions stables). Source unique côté back : `gradle/libs.versions.toml` ; les bibliothèques gérées par le BOM Spring Boot suivent le BOM et n'y sont pas versionnées.

- Java : 25 (LTS), via toolchain Gradle (JDK téléchargé automatiquement par foojay s'il est absent)
- Gradle : 9.8.0 (wrapper)
- Spring Boot : 4.1.1 — BOM : Spring Framework 7.0.9, Spring Batch 6.0.5
- PostgreSQL : 18 (image `postgres:18.6`), pilote JDBC 42.7.13 (BOM)
- jOOQ : 3.21.7 (BOM, runtime et codegen) — Flyway : 12.4.0 (BOM ; image `flyway/flyway:12.4.0-alpine` pour la commande de migration)
- Tests : JUnit Jupiter 6.0.3, AssertJ 3.27.7, Testcontainers 2.0.5 (BOM) ; ArchUnit 1.5.1, WireMock 3.13.2 (standalone), swagger-request-validator 3.0.0
- OpenAPI Generator (plugin Gradle) : 7.25.0
- Node : 24 LTS (24.21.0) — Angular : 22.2 — TypeScript : 6.0 (imposé par Angular 22)
- Front outillage : Vitest 5.0, ESLint 10 + angular-eslint 22.5, Playwright 1.63, @axe-core/playwright 4.13
- ECharts : 6.1 via ngx-echarts 22.0

## Organisation du build

- Paquet racine Java : `fr.suivons.<module>` (ex. `fr.suivons.ingestion.decp`), groupe Gradle `fr.suivons`.
- Un module applique **un** plugin de convention : `suivons.java-library` (bibliothèque), `suivons.spring-boot-app` (exécutable), `suivons.database` (`db` uniquement : codegen jOOQ et commande de migration) ou `suivons.openapi-contract` (`contract` uniquement). Aucune version ni configuration de compilation dans les `build.gradle.kts` des modules.
- Code jOOQ : généré par `:db:generateJooq` (PostgreSQL éphémère via Testcontainers + migrations Flyway), **versionné** dans `db/src/main/jooq`, régénéré et commité avec chaque migration. Migrations dans `db/src/main/resources/db/migration`.
- Contrat : `contract/openapi.yaml` (OpenAPI 3.0.3). `./gradlew build` le valide, génère les interfaces Spring (`fr.suivons.contract.api`, DTO dans `fr.suivons.contract.model`, compilés dans `contract`) et le client Angular (`contract/build/generated/typescript-angular`). Le schéma `Problem` (RFC 9457) est porté par `org.springframework.http.ProblemDetail` côté Spring, jamais par un DTO. Les conventions transverses du contrat sont vérifiées par `ContractConventionsTest`.
- API : contrôleurs de `fr.suivons.api` préfixés par `/api/v1` (`ApiPathConfig`, égal au serveur du contrat) ; Actuator sur `/actuator` (`health` avec sondes, `info`) ; logs JSON ECS ; erreurs RFC 9457. L'API ne migre jamais le schéma : Flyway n'est sur son classpath qu'en TI.
- Front : client d'API généré par `npm run generate:api` (Gradle) dans `front/src/app/core/api/generated`, non versionné, branché par `provideApi('/api/v1')` ; rendu serveur à la demande (`RenderMode.Server`) ; design tokens dans `front/src/styles/_tokens.scss` (thèmes clair et sombre, `prefers-reduced-motion`).
- Image PostgreSQL des TI et du codegen : `postgres-image` du catalogue (propriété système `suivons.postgres.image` dans les TI) ; `docker-compose.yml` doit rester aligné.
- Suites de tests : `test` (TU, dans `./gradlew build`) et `integrationTest` (TI, `src/integrationTest/java`, hors `build`).

## Commandes

```bash
docker compose up -d db                 # PostgreSQL local
./gradlew :db:migrate                   # appliquer les migrations (commande dédiée, ADR 0005 ; voir docs/exploitation.md)
./gradlew build                         # compile + TU + ArchUnit
./gradlew integrationTest               # TI (Testcontainers)
./gradlew :db:generateJooq              # régénérer le code jOOQ après une migration (Docker requis)
npx @redocly/cli@2.54.3 lint --config contract/redocly.yaml contract/openapi.yaml   # lint du contrat
SPRING_PROFILES_ACTIVE=dev ./gradlew :api:bootRun   # API locale sur la base du docker-compose
./gradlew :ingestion-decp:bootRun --args='--run'   # lancer une ingestion en local
cd front && npm ci                      # dépendances front (Node 24, voir front/.nvmrc)
cd front && npm test                    # TU front (Vitest) ; régénère d'abord le client d'API
cd front && npm run lint                # ESLint (angular-eslint)
cd front && npm run e2e                 # TS Playwright + axe-core (serveur SSR ; stack docker-compose à partir du lot 2)
```

## CI

`.github/workflows/ci.yml`, sur chaque pull request et sur `main` : jobs `back` (build, TU, ArchUnit, TI, code jOOQ à jour), `contrat` (lint Redocly), `front` (lint, TU, TS Playwright + axe-core) et `dependances` (`npm audit`, graphe Gradle, revue des vulnérabilités et des licences incompatibles avec l'AGPL sur les PR). Tous bloquants. Dependabot (`.github/dependabot.yml`) propose les mises à jour chaque semaine.

Branche par défaut : `main`. Toute évolution passe par une PR vers `main` ; fusion uniquement avec la CI verte.

## Règles d'architecture (non négociables)

- **Modularité** : un module `ingestion-<source>` ne dépend jamais d'un autre. `api` ne dépend ni des modules `ingestion-*`, ni de `reconciliation`. `domain` ne dépend ni de Spring, ni de jOOQ. Ces règles sont vérifiées par ArchUnit (module `architecture`, dans `./gradlew build`) : ne pas les désactiver. Toute nouvelle règle vient avec son autotest (classe en infraction volontaire dans un paquet `*.violation`).
- **API-first** : toute évolution d'API commence par `contract/openapi.yaml`, puis régénération des interfaces Spring et du client Angular. Ne jamais écrire à la main un DTO qui existe dans le contrat.
- **Accès données** : jOOQ uniquement. Pas de JPA ni d'Hibernate.
- **Schéma** : uniquement via une nouvelle migration Flyway. Ne jamais modifier une migration déjà fusionnée.
- **API d'abord** : une donnée disponible via une API externe n'est pas recopiée en base, sauf les champs justifiés dans `SPEC.md` §6.3 (ADR 0004). `referentiel-client` n'écrit jamais en base.
- **Écriture** : seul `ingestion-core` écrit dans `core.flux`. L'API n'écrit que dans `ops.signalement`.
- **Nouvelle source** : un nouveau module qui implémente les points d'extension d'`ingestion-core`, plus une mise à jour de `SPEC.md` §4 et de la méthodologie.

## Règles métier (non négociables)

- **Provenance** : tout flux a `source_code`, `source_record_id`, `source_url`, `run_id` et `extrait_le`. Aucun contournement.
- **Idempotence** : upsert par `(source_code, source_record_id)`. Rejouer un run ne change rien.
- **Montants** : entiers en euros ; bornes ferme / plafond selon `SPEC.md` §6.4 ; les flux `ABERRANT` n'entrent dans aucun agrégat.
- **Rattachement** : en cas de doute, ne pas rattacher (`NON_RESOLU`). Le seuil de confiance est un paramètre, jamais une constante en dur.
- **Vie privée** : jamais d'exposition nominative d'une entreprise en diffusion partielle ou d'une personne physique.
- **Vocabulaire** : dans l'interface et l'API, uniquement des termes neutres (voir `SPEC.md` §9.5). Jamais « fraude », « corruption », « suspect ».

## Tests

- Toute modification est livrée avec ses tests : TU systématiques, TI dès qu'il y a du SQL, un job ou un endpoint, TS pour tout nouveau parcours utilisateur.
- TI sur **vrai PostgreSQL via Testcontainers**. Jamais de H2.
- Ingestion : fixture + golden file + test d'idempotence pour chaque cas piège.
- API externes simulées par WireMock en TI : aucun appel réel en CI. Clés uniquement en secrets d'environnement (`INSEE_API_KEY`).
- Les réponses API sont validées contre `openapi.yaml` en TI.
- Front : chaque graphique a une alternative tableau testée ; axe-core dans les TS.
- Ne jamais supprimer, désactiver ou affaiblir un test pour faire passer la CI : corriger le code, ou signaler le problème.

## Conventions de code

- **Nommage** : identifiants techniques en anglais (`Repository`, `Service`, `Controller`, `Job`), termes métier en français sans accents, alignés sur le glossaire de `SPEC.md` §3 (`Flux`, `Payeur`, `montantFerme`, `canal`).
- **Java** : records pour les objets immuables, pas de `null` retourné par les méthodes publiques (utiliser `Optional`), exceptions métier explicites.
- **Angular** : composants standalone, signals, formulaires typés ; aucun formatage de montant en dehors du pipe dédié ; couleurs uniquement via les design tokens.
- **Commits** : Conventional Commits (`feat(decp): …`, `fix(api): …`, `test(reconciliation): …`).
- **Décisions d'architecture** : toute décision structurante est consignée en ADR dans `docs/adr/`.

## Façon de travailler

1. Lire `SPEC.md` et les ADR concernés.
2. Proposer un plan court avant toute tâche de plus d'un fichier.
3. Travailler par lot (`SPEC.md` §12) ; ne pas anticiper un lot suivant sans accord.
4. En cas d'ambiguïté ou de contradiction avec la spec : poser la question, ne pas trancher seul.
5. À la fin d'une tâche : tests au vert, documentation mise à jour (spec, méthodologie, ADR si besoin), résumé des changements et des points ouverts.
