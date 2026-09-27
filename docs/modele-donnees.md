# Modèle de données

> État au lot 1, étape 2 (migrations `V1` à `V5`). Référence : `SPEC.md` §6. Les tables `raw.*` (une par source de flux) arrivent avec chaque ingestion, `mart.*` avec l'API (lot 3), `ops.signalement` au lot 6.

```mermaid
erDiagram
    OPS_SOURCE ||--o{ OPS_INGESTION_RUN : "alimente"
    OPS_INGESTION_RUN ||--o{ OPS_REJET : "trace"
    OPS_INGESTION_RUN ||--o{ CORE_FLUX : "a chargé"
    OPS_SOURCE ||--o{ CORE_FLUX : "provenance"
    CORE_ENTREPRISE |o--o{ CORE_FLUX : "bénéficiaire (si rattaché)"
    CORE_PAYEUR |o--o{ CORE_FLUX : "payeur"
    CORE_NAF |o..o{ CORE_ENTREPRISE : "activité (sans contrainte)"

    OPS_SOURCE {
        text code PK "SIRENE, RECHERCHE_ENTREPRISES, DECP, TAM, KOHESIO"
        text libelle
        text producteur
        text licence
        text url_reference
        text frequence
    }
    OPS_INGESTION_RUN {
        bigint id PK
        text source_code FK
        text version_source
        timestamptz debut
        timestamptz fin
        text statut "EN_COURS, SUCCES, ECHEC"
        int lus
        int charges
        int rejetes
        text checksum_fichier
    }
    OPS_REJET {
        bigint id PK
        bigint run_id FK
        text source_record_id
        text motif
        jsonb payload
    }
    CORE_NAF {
        text nomenclature PK "NAFRev2, NAF2025"
        text code PK
        text libelle
    }
    CORE_ENTREPRISE {
        char siren PK
        text denomination
        text naf_code
        text naf_nomenclature
        text commune_siege
        text departement_siege
        text etat "ACTIVE, CESSEE"
        boolean diffusible "faux : jamais nommée"
        boolean personne_physique "vrai : jamais nommée"
        timestamptz rafraichi_le
    }
    CORE_PAYEUR {
        bigint id PK
        text identifiant UK
        text nom
        text type "ETAT, OPERATEUR, COLLECTIVITE, HOPITAL, UE, AUTRE"
    }
    CORE_FLUX {
        bigint id PK
        smallint annee PK "clé de partition (2010-2030 + hors plage)"
        text canal "MARCHE, AIDE_ETAT, FONDS_UE"
        char beneficiaire_siren FK
        text beneficiaire_nom_source
        bigint payeur_id FK
        date date_flux
        bigint montant_ferme "borne basse"
        bigint montant_plafond "borne haute"
        text nature_montant "FERME, PLAFOND, PARTAGE, INCONNU"
        text qualite "OK, ABERRANT"
        text rattachement "SIREN_SOURCE, RESOLU_AUTO, NON_RESOLU"
        numeric confiance_rattachement
        text source_code FK
        text source_record_id "unique avec source_code"
        text source_url "obligatoire"
        bigint run_id FK
        timestamptz extrait_le
    }
```

## Règles portées par la base

- **Provenance** : `source_code`, `source_record_id`, `source_url`, `run_id`, `extrait_le` obligatoires sur chaque flux.
- **Montants** : entiers en euros, jamais négatifs ; `montant_ferme` ≤ `montant_plafond` ; un flux `FERME` a deux bornes égales ; seul un flux `INCONNU` peut n'avoir aucun montant.
- **Rattachement** : un flux `NON_RESOLU` n'a jamais de SIREN, un flux rattaché en a toujours un ; `SIREN_SOURCE` implique une confiance de 1.
- **Année** : `annee` = année de `date_flux` ; chaque flux est rangé dans la partition de son année.

## Qui peut faire quoi

| Rôle | `ops` | `raw` | `core` | `mart` | Structure (DDL) |
|---|---|---|---|---|---|
| Utilisateur de migration | tout | tout | tout | tout | **oui** (seul) |
| `suivons_ingestion` | lecture et écriture (`source` en lecture) | lecture et écriture | lecture et écriture | lecture et écriture | non |
| `suivons_api` | lecture de `source` et `ingestion_run` | aucun accès | lecture | lecture | non |

Tables techniques non représentées : `ops.batch_*` (Spring Batch) et `public.flyway_schema_history`.
