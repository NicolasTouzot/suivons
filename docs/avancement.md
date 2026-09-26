# Avancement

> Note de passation entre sessions. À lire après `CLAUDE.md` et `SPEC.md`, et à tenir à jour en fin de session.

## Lot 0 — Socle (en cours)

Plan validé, exécuté par étapes, avec un point d'étape auprès du porteur après chacune :

| Étape | Contenu | État |
|-------|---------|------|
| 0 | Amorçage : spec, conventions, licence, ADR 0001-0003 | ✅ fait |
| — | ADR 0004 « référentiel API d'abord », SPEC v0.2 | ✅ fait |
| 1 | Figer les versions (dernières stables), wrapper Gradle, catalogue `gradle/libs.versions.toml`, report dans `CLAUDE.md` | ✅ fait |
| 2 | Monorepo Gradle : modules du §7.2 (dont `referentiel-client`), convention plugins, source set `integrationTest` | ⏭️ prochaine |
| 3 | `docker-compose` PostgreSQL (`pg_trgm`, `unaccent`), module `db` : `V1__init.sql` (4 schémas + extensions, aucune table), codegen jOOQ via Testcontainers | à faire |
| 4 | `contract` : `openapi.yaml` vide (Problem RFC 9457), lint, génération Spring + client TS câblée | à faire |
| 5 | `api` minimal : démarrage, Actuator, logs JSON, TI | à faire |
| 6 | Règles ArchUnit (§7.2, dont `referentiel-client` sans `db`) | à faire |
| 7 | `front` Angular standalone + SSR, Vitest, ESLint ; Playwright smoke + axe-core | à faire |
| 8 | CI GitHub Actions (build, TU, TI, lint front, TS, scan dépendances et licences compatibles AGPL) | à faire |
| 9 | Spike sources → `docs/sources/` (endpoints, formats, volumétrie, identifiants, écarts à la spec) | à faire |

## Décisions prises

- Licence AGPL-3.0-or-later (ADR 0003). Conséquence : lien « Code source » dans le front (lot 6 au plus tard), dépendances compatibles AGPL.
- Pas de copie de SIRENE ; référentiel minimal des bénéficiaires, identité lue en direct (ADR 0004).
- API INSEE : **API Sirene 3.11**, plan « Accès public » (clé API, 30 req/min, 2 000 req/h). La clé est lue dans `INSEE_API_KEY`, en production comme dans l'environnement cloud Claude (variable d'environnement classique). Le client n'envoie l'en-tête `X-INSEE-Api-Key-Integration` **que** sur `/api-sirene/*`.

- Versions : les bibliothèques gérées par le BOM Spring Boot suivent le BOM (ex. Flyway 12.4 et non 13.8, jOOQ 3.21.7 et non 3.21.9) ; seules les dépendances hors BOM sont prises à leur dernière version. PostgreSQL 18 (la 19 est en bêta). TypeScript 6.0 et non 7.0 (contrainte d'Angular 22).
- API INSEE : vérifié le 2026-09-26, `GET https://api.insee.fr/api-sirene/3.11/siren/552032534` répond 200 avec la clé injectée par le proxy. Cette injection s'appliquait à tout `api.insee.fr` et faisait échouer **Melodi** (`/melodi/*`, API ouverte sans abonnement possible) en 401 : injection proxy abandonnée au profit de la variable d'environnement. Revérifié le 2026-09-26 : Sirene 200 avec `INSEE_API_KEY`, 401 sans ; Melodi 200 sans clé.

## Environnement cloud : outillage (constaté le 2026-09-26)

- JDK installé : 21 ; le JDK 25 de la toolchain est téléchargé par Gradle (foojay/Adoptium joignables).
- Node installé : 22.22.2, **inférieur au minimum d'Angular 22** (`^22.22.3 || ^24.15.0`) : installer Node 24 avant l'étape 7.
- Docker : client présent, **démon non démarré** (`/var/run/docker.sock` absent) : à résoudre avant l'étape 3 (Testcontainers, codegen jOOQ).

## Accès réseau depuis l'environnement cloud (constaté le 2026-09-26)

| Hôte | État |
|------|------|
| `files.data.gouv.fr`, `object.files.data.gouv.fr`, `static.data.gouv.fr` | ✅ |
| `api.insee.fr`, `portail-api.insee.fr`, `api-apimanager.insee.fr` | ✅ |
| `ec.europa.eu`, `webgate.ec.europa.eu`, `kohesio.ec.europa.eu` | ✅ |
| Maven Central, Gradle, npm | ✅ |
| `www.data.gouv.fr`, `recherche-entreprises.api.gouv.fr`, `tabular-api.data.gouv.fr` | ❌ connexion coupée juste après son établissement (probable filtrage des IP de datacenter) : fournir les URL de ressources à la main, ou faire le spike en session locale |

## Points ouverts

- API INSEE **Melodi** (`https://api.insee.fr/melodi`) : jeux statistiques potentiellement utiles ; à explorer au spike (étape 9), usage à valider (SPEC §4/§6.3, ADR 0004).

- API Recherche d'entreprises : limite documentée (openapi du 2026-09-26) de **7 req/s par IP et 30 req/s par ASN**, avec 429 et `Retry-After` ; l'en-tête `User-Agent` descriptif est recommandé. Depuis l'environnement cloud, retesté le 2026-09-26 : la connexion TLS s'établit via le proxy, puis l'amont coupe (39 octets reçus, `ws_closed_mid_exchange`), alors que `annuaire-entreprises.data.gouv.fr` répond 200. Il s'agit donc d'un filtrage côté fournisseur (IP ou ASN du datacenter), et non du proxy. Conséquences : spike à faire en session locale ; **hébergement de production** à choisir en tenant compte de la limite par ASN (le repli de recherche F1 de l'`api` en dépend).
- Quota API Sirene suffisant face aux robots d'indexation (lot 7).
