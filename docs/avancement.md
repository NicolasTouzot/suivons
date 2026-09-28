# Avancement

> Note de passation entre sessions. À lire après `CLAUDE.md` et `SPEC.md`, et à tenir à jour en fin de session.

## Lot 0 — Socle (terminé)

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
| 8 | CI GitHub Actions (build, TU, TI, lint front, TS, scan dépendances et licences compatibles AGPL) | ✅ fait |
| 9 | Spike sources → `docs/sources/` (endpoints, formats, volumétrie, identifiants, écarts à la spec) | ✅ fait (compléments en session locale, voir `docs/sources/README.md`) |

## Lot 1 — Référentiel (terminé)

Plan validé le 2026-09-27, une PR vers `main` par étape :

| Étape | Contenu | État |
|-------|---------|------|
| 1 | Schéma : `ops` (sources, runs, rejets, tables Spring Batch), `core` (naf, entreprise, payeur, flux partitionné), rôles `suivons_ingestion` et `suivons_api`, codegen jOOQ, TI du schéma | ✅ fait |
| 2 | Commande de migration dédiée (ADR 0005) : `./gradlew :db:migrate`, image Flyway en production (`docs/exploitation.md`) | ✅ fait |
| 3 | `referentiel-client` : clients Sirene et Recherche d'entreprises, quotas, cache (Caffeine), disjoncteur (Resilience4j), mode dégradé | ✅ fait |
| 4 | `ingestion-core` : pipeline §7.3 en job Spring Batch, points d'extension, idempotence, règle ArchUnit « seul ingestion-core écrit dans core.flux » | ✅ fait |
| 5 | `ingestion-sirene` : rafraîchissement de `core.entreprise`, chargement de `core.naf` (fichiers INSEE) | ✅ fait |

Décisions du lot : clients d'API sans Spring Boot côté logique (configuration Spring à importer explicitement, `ReferentielClientConfiguration`) ; une entité en diffusion partielle garde sa dénomination dans Sirene (personne morale) : le masquage relève du référentiel et de l'affichage ; **tranche verticale** après le lot 1 (lot 1 bis, SPEC §12 : un extrait DECP réel jusqu'à une fiche entreprise dans le front) et **démo dans chaque PR** (décidé le 2026-09-27, pour rendre le travail visible plus tôt) ; `core.flux` et `core.payeur` créés dès le lot 1 (nécessaires à `ingestion-core`) ; tables Spring Batch dans `ops` (préfixe `batch_`) ; NAF chargée depuis les fichiers INSEE, NAF 2025 prévue dans le modèle ; Resilience4j et Caffeine ajoutés.

Étape 5 (2026-09-28) :
- `./gradlew :ingestion-sirene:bootRun --args='--run'` enchaîne deux runs indépendants (code de sortie 1 si l'un échoue) : chargement de la NAF (source `NAF`, ajoutée à `ops.source` par `V6`) puis rafraîchissement du référentiel (source `SIRENE`).
- NAF : NAF rév. 2 (`.xls`, 732 sous-classes) et NAF 2025 (`.xlsx`, 747 sous-classes) lues avec Apache POI 5.5.1 ; chaque fichier est contrôlé par son empreinte SHA-256 (`suivons.naf.fichiers`) : un fichier republié par l'INSEE fait échouer le run sans rien modifier, jusqu'à vérification et mise à jour de l'empreinte.
- Référentiel : job Spring Batch en deux étapes. D'abord le retrait des entreprises sans flux, non rafraîchies depuis `suivons.referentiel-minimal.delai-grace` (1 jour : une entreprise ajoutée par un rattachement dont les flux ne sont pas encore écrits est protégée). Ensuite la relecture de toutes les autres par lots de 1 000 (unités légales et sièges, deux appels Sirene et une transaction par lot). Seules les lignes qui changent sont réécrites ; `rafraichi_le` est la date de la dernière vérification. SIREN inconnu de l'INSEE : conservé tel quel, tracé dans `ops.rejet`. Quota épuisé ou API indisponible : run en échec, lots déjà traités acquis.
- Vie privée : la dénomination d'une entreprise non diffusible ou d'une personne physique n'est **jamais** écrite dans `core.entreprise`. Une activité codée dans une nomenclature antérieure à la NAF rév. 2 (entreprises anciennes) n'est pas conservée (`naf_code` vide).
- Règle ArchUnit « seul ingestion-core écrit dans core.flux » : elle juge au niveau de la classe. La lecture des flux (`EntreprisesSansFlux`) est donc séparée de l'écriture des entreprises (`DepotEntreprises`).

## Lot 1 bis — Tranche verticale (en cours)

Plan validé le 2026-09-28, une PR vers `main` par étape :

| Étape | Contenu | État |
|-------|---------|------|
| 1 | `ingestion-decp` minimale : un mois réel (format 2022), un flux par (marché, titulaire), rattachement par SIRET, `raw.decp_record` (`V7`) | ✅ fait |
| 2 | `GET /entreprises/{siren}` et `GET /entreprises/{siren}/flux` (contrat d'abord) : identité minimale, total tracé, flux avec lien vers la source | ✅ fait |
| 3 | Fiche entreprise dans le front, stack `docker-compose` complète, TS Playwright + axe-core | ✅ fait (PR en revue) |

Étape 1 (2026-09-28) :
- Écriture du référentiel minimal déplacée d'`ingestion-sirene` vers `reconciliation` (`fr.suivons.reconciliation.referentiel` : `ReferentielMinimal`, `DepotEntreprises`, `EntreprisesSansFlux`), décidé le 2026-09-28 : partagée par le rafraîchissement SIRENE et le rattachement des sources.
- `Rattacheur.rattacherLot` : le pipeline rattache chaque lot (chunk) en une fois, pour grouper les appels Sirene (1 000 SIREN par requête). Une erreur de l'API fait échouer le run (jamais un `NON_RESOLU` par défaut) ; ses données brutes sont retraitées au run suivant.
- DECP : export JSON filtré sur un mois (`suivons.decp.mois`, variable `SUIVONS_DECP_MOIS`), garde-fou contre les réindexations du producteur. Règles minimales de fusion (`FusionDecp`) et de bornes (`TransformationDecp`) : dernière publication, montant de la dernière modification sinon montant le plus élevé, groupement → `PARTAGE`, accord-cadre → `PLAFOND`, `ABERRANT` si ≤ 1 € ou > 1 Md€. Payeur = SIRET de l'acheteur, type `AUTRE` (nom et catégorie via Sirene plus tard). Lien vers la source : page du jeu filtrée sur le marché.
- `Siren.lire` accepte les SIRET de La Poste (SIREN 356000000), dont la clé n'est pas celle de Luhn (somme des chiffres multiple de 5).

Étape 2 (2026-09-28) :
- Deux endpoints, conformément au SPEC §8 : synthèse (`/entreprises/{siren}` : identité minimale, bornes, nombre de flux et de flux aberrants, période, répartition par canal, lien vers l'Annuaire des entreprises) et détail (`/entreprises/{siren}/flux`, paginé, du plus récent au plus ancien, avec la source de chaque flux).
- Lecture directe de `core` en attendant le mart (lot 3). Les flux `ABERRANT` sont comptés à part et exclus des montants ; ils restent visibles dans le détail. Borne absente = 0 dans les sommes.
- `nomMasque` (diffusion partielle ou entrepreneur individuel) : la dénomination n'est jamais renvoyée, même si elle était en base.
- Entreprise absente du référentiel (aucun flux) : 404 « Aucun flux tracé » ; la fiche construite depuis Sirene viendra avec `/identite`. SIREN mal formé ou clé de Luhn invalide : 400. Pagination à partir de 1, `size` ≤ 100.
- Contraintes du contrat appliquées par `spring-boot-starter-validation` ; leurs violations sont traduites en 400 RFC 9457 (`ErreursApi`). Réponses validées contre `openapi.yaml` en TI (swagger-request-validator), comme prévu dès le lot 0.

Étape 3 (2026-09-28) :
- Front : accueil avec recherche par SIREN ou SIRET (le nom au lot 3), fiche `/entreprises/:siren` rendue côté serveur (identité, montant attribué en chiffre principal, montant maximal possible, canaux, tableau paginé des flux avec lien vers la source), messages pour une entreprise sans flux tracé et pour une entreprise non nommée. `MontantPipe` (exact ou compact) seul formateur de montants ; libellés neutres (`core/libelles.ts`) ; une couleur par canal dans les tokens.
- Le serveur SSR relaie `/api` (lecture seule) vers l'API (`API_URL`) : même origine pour le navigateur et le rendu serveur, pas de CORS.
- Stack `docker-compose` (profil `stack`) : images de l'API et du front construites sur des artefacts préparés hors Docker (jar, bundle SSR), pour garder des images simples et un seul outillage de build. Jeu de démonstration `fixtures/demo/parcours.sql` (extrait réel de juin 2026 + entreprise fictive non nommable).
- CI : job `parcours` (stack complète, jeu de démonstration, TS Playwright + axe-core en bureau et mobile). La stack est jugée prête quand l'API répond à travers le relais du front.
- Corrigé pendant les TS : les textes réservés aux lecteurs d'écran du tableau élargissaient la page mobile (955 px pour 375) ; test de non-débordement ajouté.

## Décisions prises

- Licence AGPL-3.0-or-later (ADR 0003). Conséquence : lien « Code source » dans le front (lot 6 au plus tard), dépendances compatibles AGPL.
- Pas de copie de SIRENE ; référentiel minimal des bénéficiaires, identité lue en direct (ADR 0004).
- Écarts du spike des sources tranchés le 2026-09-27 (SPEC v0.3, `docs/sources/README.md`) : DECP lu chez la DAJ (2 formats), un flux DECP par (marché, titulaire), TAM par fenêtres de dates avec l'élément d'aide comme montant, Kohesio multi-bénéficiaires en `PARTAGE`, masquage si diffusion partielle ou entrepreneur individuel, Recherche d'entreprises conservée (blocage anti-robots propre au cloud de dev), registre de minimis en V2.
- Migrations appliquées par une commande dédiée avec un rôle de migration, jamais par les applications (ADR 0005).
- API INSEE : **API Sirene 3.11**, plan « Accès public » (clé API, 30 req/min, 2 000 req/h). La clé est lue dans `INSEE_API_KEY`, en production comme dans l'environnement cloud Claude (variable d'environnement classique). Le client n'envoie l'en-tête `X-INSEE-Api-Key-Integration` **que** sur `/api-sirene/*`.

- Build (étape 2) : plugins de convention dans `build-logic` ; paquet racine `fr.suivons` (lié au nom de travail, à renommer si le nom définitif change, §14). Chaque application a une classe `*Application` et un TU de démarrage du contexte ; `contract` est une bibliothèque Java qui recevra les interfaces générées (étape 4).
- Base (étape 3) : codegen jOOQ par une tâche maison (`build-logic`, `GenerateJooqTask`) plutôt que le plugin jOOQ officiel, qui ne sait pas appliquer Flyway sur un PostgreSQL éphémère. Tant qu'il n'y a pas de table, jOOQ ne génère rien (catalogue vide exclu) : c'est normal. Table d'historique Flyway dans `public`. Les rôles PostgreSQL distincts (§11.4) seront créés avec les premières tables (lot 1).
- Contrat (étape 4) : OpenAPI **3.0.3** et non 3.1, pour rester compatible avec swagger-request-validator (validation des réponses en TI) ; à réévaluer si l'outillage suit. Base `/api/v1`, schéma `Problem` et réponses d'erreur partagées (`BadRequest`, `NotFound`, `TooManyRequests`, `ServiceUnavailable`), aucun endpoint. Générateur Spring en mode interfaces seules (`useSpringBoot4`, `useJackson3`, `useTags`) ; `Problem` → `ProblemDetail`. Client Angular généré dans `contract/build/generated/typescript-angular` : branchement dans `/front` à l'étape 7. Lint Redocly (`recommended`) lancé via `npx`, intégré à la CI à l'étape 8.
- API (étape 5) : starters Boot 4 `webmvc`, `actuator`, `jooq` ; threads virtuels ; datasource par variables d'environnement `SPRING_DATASOURCE_*` (profil `dev` aligné sur docker-compose). Flyway uniquement sur le classpath des TI de l'API (migrations du module `db` appliquées au démarrage du test) : migrations en production par une **commande dédiée** avec un rôle de migration (ADR 0005, décidé le 2026-09-27), forme concrète au lot 1 avec les rôles PostgreSQL. Le TU de démarrage de l'API est remplacé par le TI `ApiApplicationIT` (l'application exige désormais une base). Validation des réponses par swagger-request-validator : à brancher avec le premier endpoint (lot 2) ; le contrat est déjà embarqué dans le jar `contract` (`classpath:openapi/openapi.yaml`).
- Architecture (étape 6) : projet Gradle de test `architecture` (hors §7.2, sans code de production) qui importe toutes les classes `fr.suivons` et vérifie : matrice des modules (couches), indépendance des sources, `api` sans ingestion ni réconciliation, `domain` pur, `referentiel-client` sans base, pas de JPA, écritures jOOQ de l'`api` confinées à `fr.suivons.api.signalement`. Autotest de chaque règle par des classes en infraction volontaire. `archRule.failOnEmptyShould=false` et couches optionnelles tant que des modules sont vides. **Reporté au lot 1** : « seul `ingestion-core` écrit dans `core.flux` » (règle sur la classe jOOQ générée de la table).
- Front (étape 7) : `ng new` Angular 22.2 (standalone, zoneless, SSR, Vitest), angular-eslint 22.5 (règles d'accessibilité des templates incluses), Playwright 1.63 + axe-core 4.13. Coque en français (`lang="fr"`, lien d'évitement, repères `header`/`main`/`footer`), tokens provisoires : l'identité visuelle (§9.2) reste à définir avec les écrans. TS de fumée : rendu serveur, contenu, axe WCAG 2.2 AA en thèmes clair et sombre, desktop et mobile 375 px. Serveur SSR : `NG_ALLOWED_HOSTS` obligatoire (hôtes de production à fixer au déploiement). Lien « Code source » (AGPL) : lot 6 comme prévu.
- CI (étape 8) : GitHub Actions, 4 jobs bloquants (`back`, `contrat`, `front`, `dependances`). Premiers runs le 2026-09-27 : `back`, `contrat` et `front` verts sur GitHub ; CI verte sur `main`. Corrections : dependency graph activé sur le dépôt ; envoi du graphe Gradle limité aux runs ayant un jeton en écriture (push, PR internes ; pas les PR Dependabot) ; liste de licences refusées restreinte aux identifiants SPDX (GPL-1.0/2.0-only, LGPL-2.0-only, SSPL, BUSL, Elastic-2.0, CC-BY-NC*, JSON ; la « Commons Clause » n'a pas d'identifiant SPDX). La revue des dépendances ne distingue pas les dépendances de test (ex. JUnit, EPL-2.0, non refusée). Dependabot : TypeScript et `@types/node` bloqués sur les plages du projet.
- Branches (2026-09-27) : `main` est la branche par défaut ; toute évolution passe par une PR vers `main`, CI verte avant fusion.
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

- Suppression des flux dont l'enregistrement disparaît de la source : non traitée par `ingestion-core` (SPEC §7.3), à décider pour DECP au lot 2.
- DECP, même (marché, titulaire) à montants différents sur une même publication : montant le plus élevé retenu au lot 1 bis, à confirmer au lot 2 (`docs/sources/decp.md`, piège 12).
- Conditions de réutilisation TAM et Kohesio à vérifier avant le lot 5 (SPEC §14).
- Pistes hors spec (de minimis, BOAMP, annuaire de l'administration, Melodi, info-financière) : `docs/sources/pistes.md`.
- Compléments du spike en session locale (Recherche d'entreprises, data.gouv.fr, conditions de réutilisation TAM et Kohesio) : `docs/sources/README.md`.
- **Hébergement de production** : la limite par ASN de l'API Recherche d'entreprises (30 req/s, voire blocage des clouds publics) est un critère de choix (`docs/sources/recherche-entreprises.md`).
- Quota API Sirene suffisant face aux robots d'indexation (lot 7).
- NAF 2025 dans Sirene : l'API 3.11 expose déjà un champ d'activité en NAF 2025 (`activitePrincipaleNAF25UniteLegale`, à confirmer) ; à lire au passage du 1er janvier 2027 (le référentiel accepte déjà `NAF2025`).
