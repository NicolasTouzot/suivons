# CLAUDE.md — Conventions du projet

> Lis `SPEC.md` avant toute tâche. La spec est la source de vérité : si une demande la contredit, signale-le au lieu de coder.

## Projet

Site citoyen qui trace l'argent public reçu par les entreprises, chaque montant relié à sa source officielle. Périmètre du MVP : `SPEC.md` §2.

## Structure du repo

```
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
/front                Application Angular
/docs                 Méthodologie, ADR (docs/adr), notes sur les sources (docs/sources)
/fixtures             Échantillons de données sources pour les tests
docker-compose.yml    PostgreSQL + stack complète pour le dev et les TS
```

## Versions

À figer au lot 0 (dernières versions stables) et à reporter ici :

- Java : _à figer_
- Spring Boot / Spring Batch : _à figer_
- PostgreSQL : _à figer_
- Angular / Node : _à figer_
- jOOQ, Flyway, Testcontainers, Playwright, ECharts : _à figer_

## Commandes

```bash
docker compose up -d db                 # PostgreSQL local
./gradlew build                         # compile + TU + ArchUnit
./gradlew integrationTest               # TI (Testcontainers)
./gradlew :db:generateJooq              # régénérer le code jOOQ après une migration
./gradlew :ingestion-decp:bootRun --args='--run'   # lancer une ingestion en local
cd front && npm test                    # TU front (Vitest)
cd front && npm run e2e                 # TS Playwright (stack docker-compose requise)
```

## Règles d'architecture (non négociables)

- **Modularité** : un module `ingestion-<source>` ne dépend jamais d'un autre. `api` ne dépend ni des modules `ingestion-*`, ni de `reconciliation`. `domain` ne dépend ni de Spring, ni de jOOQ. Ces règles sont vérifiées par ArchUnit : ne pas les désactiver.
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
