# Avancement

> Note de passation entre sessions. À lire après `CLAUDE.md` et `SPEC.md`, et à tenir à jour en fin de session.

## Lot 0 — Socle (en cours)

Plan validé, exécuté par étapes, avec un point d'étape auprès du porteur après chacune :

| Étape | Contenu | État |
|-------|---------|------|
| 0 | Amorçage : spec, conventions, licence, ADR 0001-0003 | ✅ fait |
| — | ADR 0004 « référentiel API d'abord », SPEC v0.2 | ✅ fait |
| 1 | Figer les versions (dernières stables), wrapper Gradle, catalogue `gradle/libs.versions.toml`, report dans `CLAUDE.md` | ✅ fait |
| 2 | Monorepo Gradle : modules du §7.2 (dont `referentiel-client`), convention plugins, source set `integrationTest` | ✅ fait |
| 3 | `docker-compose` PostgreSQL (`pg_trgm`, `unaccent`), module `db` : `V1__init.sql` (4 schémas + extensions, aucune table), codegen jOOQ via Testcontainers | ✅ fait |
| 4 | `contract` : `openapi.yaml` vide (Problem RFC 9457), lint, génération Spring + client TS câblée | ✅ fait |
| 5 | `api` minimal : démarrage, Actuator, logs JSON, TI | ✅ fait |
| 6 | Règles ArchUnit (§7.2, dont `referentiel-client` sans `db`) | ✅ fait |
| 7 | `front` Angular standalone + SSR, Vitest, ESLint ; Playwright smoke + axe-core | ✅ fait |
| 8 | CI GitHub Actions (build, TU, TI, lint front, TS, scan dépendances et licences compatibles AGPL) | ✅ fait (à confirmer au premier run) |
| 9 | Spike sources → `docs/sources/` (endpoints, formats, volumétrie, identifiants, écarts à la spec) | ⏭️ prochaine |

## Décisions prises

- Licence AGPL-3.0-or-later (ADR 0003). Conséquence : lien « Code source » dans le front (lot 6 au plus tard), dépendances compatibles AGPL.
- Pas de copie de SIRENE ; référentiel minimal des bénéficiaires, identité lue en direct (ADR 0004).
- Migrations appliquées par une commande dédiée avec un rôle de migration, jamais par les applications (ADR 0005).
- API INSEE : **API Sirene 3.11**, plan « Accès public » (clé API, 30 req/min, 2 000 req/h). La clé est lue dans `INSEE_API_KEY`, en production comme dans l'environnement cloud Claude (variable d'environnement classique). Le client n'envoie l'en-tête `X-INSEE-Api-Key-Integration` **que** sur `/api-sirene/*`.

- Build (étape 2) : plugins de convention dans `build-logic` ; paquet racine `fr.suivons` (lié au nom de travail, à renommer si le nom définitif change, §14). Chaque application a une classe `*Application` et un TU de démarrage du contexte ; `contract` est une bibliothèque Java qui recevra les interfaces générées (étape 4).
- Base (étape 3) : codegen jOOQ par une tâche maison (`build-logic`, `GenerateJooqTask`) plutôt que le plugin jOOQ officiel, qui ne sait pas appliquer Flyway sur un PostgreSQL éphémère. Tant qu'il n'y a pas de table, jOOQ ne génère rien (catalogue vide exclu) : c'est normal. Table d'historique Flyway dans `public`. Les rôles PostgreSQL distincts (§11.4) seront créés avec les premières tables (lot 1).
- Contrat (étape 4) : OpenAPI **3.0.3** et non 3.1, pour rester compatible avec swagger-request-validator (validation des réponses en TI) ; à réévaluer si l'outillage suit. Base `/api/v1`, schéma `Problem` et réponses d'erreur partagées (`BadRequest`, `NotFound`, `TooManyRequests`, `ServiceUnavailable`), aucun endpoint. Générateur Spring en mode interfaces seules (`useSpringBoot4`, `useJackson3`, `useTags`) ; `Problem` → `ProblemDetail`. Client Angular généré dans `contract/build/generated/typescript-angular` : branchement dans `/front` à l'étape 7. Lint Redocly (`recommended`) lancé via `npx`, intégré à la CI à l'étape 8.
- API (étape 5) : starters Boot 4 `webmvc`, `actuator`, `jooq` ; threads virtuels ; datasource par variables d'environnement `SPRING_DATASOURCE_*` (profil `dev` aligné sur docker-compose). Flyway uniquement sur le classpath des TI de l'API (migrations du module `db` appliquées au démarrage du test) : migrations en production par une **commande dédiée** avec un rôle de migration (ADR 0005, décidé le 2026-09-27), forme concrète au lot 1 avec les rôles PostgreSQL. Le TU de démarrage de l'API est remplacé par le TI `ApiApplicationIT` (l'application exige désormais une base). Validation des réponses par swagger-request-validator : à brancher avec le premier endpoint (lot 2) ; le contrat est déjà embarqué dans le jar `contract` (`classpath:openapi/openapi.yaml`).
- Architecture (étape 6) : projet Gradle de test `architecture` (hors §7.2, sans code de production) qui importe toutes les classes `fr.suivons` et vérifie : matrice des modules (couches), indépendance des sources, `api` sans ingestion ni réconciliation, `domain` pur, `referentiel-client` sans base, pas de JPA, écritures jOOQ de l'`api` confinées à `fr.suivons.api.signalement`. Autotest de chaque règle par des classes en infraction volontaire. `archRule.failOnEmptyShould=false` et couches optionnelles tant que des modules sont vides. **Reporté au lot 1** : « seul `ingestion-core` écrit dans `core.flux` » (règle sur la classe jOOQ générée de la table).
- Front (étape 7) : `ng new` Angular 22.2 (standalone, zoneless, SSR, Vitest), angular-eslint 22.5 (règles d'accessibilité des templates incluses), Playwright 1.63 + axe-core 4.13. Coque en français (`lang="fr"`, lien d'évitement, repères `header`/`main`/`footer`), tokens provisoires : l'identité visuelle (§9.2) reste à définir avec les écrans. TS de fumée : rendu serveur, contenu, axe WCAG 2.2 AA en thèmes clair et sombre, desktop et mobile 375 px. Serveur SSR : `NG_ALLOWED_HOSTS` obligatoire (hôtes de production à fixer au déploiement). Lien « Code source » (AGPL) : lot 6 comme prévu.
- CI (étape 8) : GitHub Actions, 4 jobs bloquants (`back`, `contrat`, `front`, `dependances`). Workflow validé par actionlint et étapes rejouées localement, mais **jamais exécuté sur GitHub** : premier run à l'ouverture d'une PR. Actions épinglées sur leur version majeure (checkout v5, setup-java v5, setup-node v5, gradle/actions v5, upload-artifact v4, dependency-review v4), non vérifiées comme dernières disponibles : Dependabot les mettra à jour. Licences refusées (liste de départ, à revoir) : GPL-1.0/2.0-only, LGPL-2.0-only, SSPL, BUSL, Elastic-2.0, CC-BY-NC*, Commons-Clause, JSON. La revue des dépendances ne distingue pas les dépendances de test (ex. JUnit, EPL-2.0, non refusée).
- Versions : les bibliothèques gérées par le BOM Spring Boot suivent le BOM (ex. Flyway 12.4 et non 13.8, jOOQ 3.21.7 et non 3.21.9) ; seules les dépendances hors BOM sont prises à leur dernière version. PostgreSQL 18 (la 19 est en bêta). TypeScript 6.0 et non 7.0 (contrainte d'Angular 22).
- API INSEE : vérifié le 2026-09-26, `GET https://api.insee.fr/api-sirene/3.11/siren/552032534` répond 200 avec la clé injectée par le proxy. Cette injection s'appliquait à tout `api.insee.fr` et faisait échouer **Melodi** (`/melodi/*`, API ouverte sans abonnement possible) en 401 : injection proxy abandonnée au profit de la variable d'environnement. Revérifié le 2026-09-26 : Sirene 200 avec `INSEE_API_KEY`, 401 sans ; Melodi 200 sans clé.

## Environnement cloud : outillage (constaté le 2026-09-26)

- JDK installé : 21 ; le JDK 25 de la toolchain est téléchargé par Gradle (foojay/Adoptium joignables).
- Node installé : 22.22.2, **inférieur au minimum d'Angular 22**. Installer Node 24 à chaque session : `curl -sSfLO https://nodejs.org/dist/v24.21.0/node-v24.21.0-linux-x64.tar.xz && tar -xJf node-v24.21.0-linux-x64.tar.xz -C /opt && mv /opt/node-v24.21.0-linux-x64 /opt/node24`, puis `export PATH=/opt/node24/bin:$PATH`.
- Playwright : Chromium préinstallé (build 1194) compatible avec Playwright 1.63 via `PLAYWRIGHT_CHROMIUM_PATH=/opt/pw-browsers/chromium-1194/chrome-linux/chrome` ; ne pas lancer `playwright install`.
- Maven Central répond parfois **429 Too Many Requests** depuis le cloud (premier téléchargement des dépendances) : relancer avec `./gradlew build --no-parallel --max-workers=1`, le cache Gradle absorbe ensuite.
- Docker : le démon n'est pas démarré à l'ouverture de session ; le lancer avec `nohup dockerd > /tmp/dockerd.log 2>&1 &` (root disponible). Testé le 2026-09-27 : Docker Hub joignable, Testcontainers et `docker compose` fonctionnent.

## Accès réseau depuis l'environnement cloud (constaté le 2026-09-26)

| Hôte | État |
|------|------|
| `files.data.gouv.fr`, `object.files.data.gouv.fr`, `static.data.gouv.fr` | ✅ |
| `api.insee.fr`, `portail-api.insee.fr`, `api-apimanager.insee.fr` | ✅ |
| `ec.europa.eu`, `webgate.ec.europa.eu`, `kohesio.ec.europa.eu` | ✅ |
| Maven Central, Gradle, npm | ✅ |
| `www.data.gouv.fr`, `recherche-entreprises.api.gouv.fr`, `tabular-api.data.gouv.fr` | ❌ connexion coupée juste après son établissement (probable filtrage des IP de datacenter) : fournir les URL de ressources à la main, ou faire le spike en session locale |

## Points ouverts

- **data.economie.gouv.fr** (API Explore v2.1 Opendatasoft, sans clé, joignable depuis le cloud), constats du 2026-09-26, à trancher avant le spike :
  - DECP consolidées par la DAJ : `decp-2022-marches-valides` (format 2022, 708 k lignes, maj 2026-09-22) et `decp-v3-marches-valides` (format 2019, 703 k), Licence Ouverte 2.0. Alternative à data.gouv.fr, qui est bloqué depuis le cloud. Écart à la spec §4 (source indiquée : data.gouv.fr).
  - `aides_minimis` : registre public des aides de minimis (DGE, Plateforme aides d'État), par bénéficiaire avec SIREN, montant en ESB, autorité d'octroi, depuis le 2026-01-01 (17,6 k lignes). Couvre les aides d'État sous le seuil TAM. Nouvelle source candidate (canal `AIDE_ETAT`) ; licence non renseignée, à vérifier.
  - Peu exploitables : `plan-de-relance` (SIREN, mais sans montant par projet), `tpe-pme-beneficiaires-des-dispositifs-france-num` (bénéficiaires pseudonymisés).
- Autres portails Opendatasoft (API Explore v2.1, sans clé, joignables depuis le cloud), constats du 2026-09-26 :
  - **BOAMP** (DILA, `boamp-datadila.opendatasoft.com`, jeu `boamp`, 1,7 M avis, maj quotidienne) : 466 k avis « Résultat de marché » depuis 2015. Le champ `titulaire` ne contient que des noms, mais le JSON eForms `donnees` porte les SIRET (`cbc:CompanyID`) et les montants attribués (`cbc:PayableAmount`, `cbc:TotalAmount`) ; `url_avis` donne un lien source par avis. Piste : contrôle croisé et complétude de DECP (canal `MARCHE`), avec un risque de doublons entre les deux sources. Licence non renseignée sur le portail.
  - **Annuaire de l'administration** (DILA, `api-lannuaire.service-public.gouv.fr`, jeu `api-lannuaire-administration`, 94 k entités, dont 44 k avec SIREN, maj quotidienne) : type d'organisme (ministère, établissement public, collectivité…) et hiérarchie. Piste : qualifier et regrouper les **payeurs** (acheteurs, autorités d'octroi).
  - **info-financiere.gouv.fr** (AMF, jeu `flux-amf-new-prod`, 537 k documents réglementés des sociétés cotées, identifiées par LEI et ISIN, sans SIREN) : aucun flux d'argent public. Piste V2 au plus : lien vers les rapports annuels depuis la fiche d'une société cotée.
- API INSEE **Melodi** (`https://api.insee.fr/melodi`) : jeux statistiques potentiellement utiles ; à explorer au spike (étape 9), usage à valider (SPEC §4/§6.3, ADR 0004).

- API Recherche d'entreprises : limite documentée (openapi du 2026-09-26) de **7 req/s par IP et 30 req/s par ASN**, avec 429 et `Retry-After` ; l'en-tête `User-Agent` descriptif est recommandé. Depuis l'environnement cloud, retesté le 2026-09-26 : la connexion TLS s'établit via le proxy, puis l'amont coupe (39 octets reçus, `ws_closed_mid_exchange`), alors que `annuaire-entreprises.data.gouv.fr` répond 200. Il s'agit donc d'un filtrage côté fournisseur (IP ou ASN du datacenter), et non du proxy. Conséquences : spike à faire en session locale ; **hébergement de production** à choisir en tenant compte de la limite par ASN (le repli de recherche F1 de l'`api` en dépend).
- Quota API Sirene suffisant face aux robots d'indexation (lot 7).
