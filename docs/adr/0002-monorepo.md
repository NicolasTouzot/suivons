# 0002 — Monorepo Gradle multi-projets + projet Angular

- Statut : Accepté
- Date : 2026-09-26

## Contexte

Le système comprend un socle de données, quatre ingestions indépendantes, une API et un front, liés par un contrat OpenAPI (`SPEC.md` §7.2). Les évolutions touchent souvent contrat, API et front en même temps.

## Décision

- Un seul dépôt : projets Gradle (`db`, `domain`, `ingestion-core`, `reconciliation`, `ingestion-*`, `contract`, `api`) et projet npm `front`.
- Dépendances entre modules limitées à celles du §7.2 et vérifiées par ArchUnit.
- Versions centralisées dans un catalogue Gradle (`gradle/libs.versions.toml`) et reportées dans `CLAUDE.md`.

## Conséquences

- Une modification du contrat, de l'API et du front passe dans une seule merge request.
- Chaque `ingestion-<source>` produit un exécutable indépendant malgré le dépôt commun.
- La CI doit construire back et front ; le coût de build est maîtrisé par le cache Gradle et npm.
