# 0005 — Migrations du schéma par une commande dédiée

- Statut : Accepté
- Date : 2026-09-27

## Contexte

Le schéma est géré exclusivement par Flyway (module `db`, SPEC.md §6). La spec impose des rôles PostgreSQL distincts : ingestion en écriture, API en lecture seule sauf `ops.signalement` (§8, §11.4). Il faut décider qui applique les migrations en production : les applications au démarrage (API, jobs d'ingestion) ou une étape à part.

## Décision

- Les migrations sont appliquées par une **commande dédiée**, lancée explicitement avant chaque déploiement (et en CI), jamais par une application au démarrage.
- Elle se connecte avec un **rôle de migration**, propriétaire des schémas, seul à détenir les droits DDL.
- Aucune application (API, `ingestion-*`) n'embarque Flyway ; leurs rôles n'ont aucun droit DDL. En test, Flyway est ajouté au seul classpath des TI pour préparer la base.
- La forme concrète (tâche Gradle pour le dev et la CI, exécutable ou image pour la production) est fixée au lot 1, avec la création des rôles PostgreSQL.

## Conséquences

- Le déploiement du schéma devient une étape visible et vérifiable, découplée du démarrage des applications ; un échec de migration ne laisse aucune application à moitié démarrée.
- Principe du moindre privilège : une application compromise ne peut pas modifier le schéma.
- Ordre de déploiement à respecter : migration, puis applications. Les migrations doivent rester compatibles avec la version des applications en cours d'exécution pendant la bascule.
