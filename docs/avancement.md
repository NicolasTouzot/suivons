# Avancement

> Note de passation entre sessions. À lire après `CLAUDE.md` et `SPEC.md`, et à tenir à jour en fin de session.

## Lot 0 — Socle (en cours)

Plan validé, exécuté par étapes, avec un point d'étape auprès du porteur après chacune :

| Étape | Contenu | État |
|-------|---------|------|
| 0 | Amorçage : spec, conventions, licence, ADR 0001-0003 | ✅ fait |
| — | ADR 0004 « référentiel API d'abord », SPEC v0.2 | ✅ fait |
| 1 | Figer les versions (dernières stables), wrapper Gradle, catalogue `gradle/libs.versions.toml`, report dans `CLAUDE.md` | ⏭️ prochaine |
| 2 | Monorepo Gradle : modules du §7.2 (dont `referentiel-client`), convention plugins, source set `integrationTest` | à faire |
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
- API INSEE : **API Sirene 3.11**, plan « Accès public » (clé API, 30 req/min, 2 000 req/h). En production, la clé est lue dans `INSEE_API_KEY`. Dans l'environnement cloud Claude, elle est injectée par le proxy (Identifiants API, hôte `api.insee.fr`) : le client ne doit pas envoyer l'en-tête si la variable est vide.

## Accès réseau depuis l'environnement cloud (constaté le 2026-09-26)

| Hôte | État |
|------|------|
| `files.data.gouv.fr`, `object.files.data.gouv.fr`, `static.data.gouv.fr` | ✅ |
| `api.insee.fr`, `portail-api.insee.fr`, `api-apimanager.insee.fr` | ✅ |
| `ec.europa.eu`, `webgate.ec.europa.eu`, `kohesio.ec.europa.eu` | ✅ |
| Maven Central, Gradle, npm | ✅ |
| `www.data.gouv.fr`, `recherche-entreprises.api.gouv.fr`, `tabular-api.data.gouv.fr` | ❌ connexion coupée juste après son établissement (probable filtrage des IP de datacenter) : fournir les URL de ressources à la main, ou faire le spike en session locale |

## Points ouverts

- Rate limit réel de l'API Recherche d'entreprises (spike).
- Quota API Sirene suffisant face aux robots d'indexation (lot 7).
