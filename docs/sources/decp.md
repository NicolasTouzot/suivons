# DECP — Données essentielles de la commande publique (canal `MARCHE`)

> Spike du 2026-09-27 (lot 0, étape 9). Chiffres mesurés sur l'export complet du jour.

## Accès

| | |
|---|---|
| Producteur | Direction des affaires juridiques (DAJ), ministère de l'Économie |
| Portail | `data.economie.gouv.fr` (Opendatasoft, API Explore v2.1, sans clé) |
| Licence | Licence Ouverte 2.0 (Etalab) |
| Jeux | `decp-2022-marches-valides` (format de l'arrêté du 22/12/2022) et `decp-v3-marches-valides` (format de l'arrêté du 22/03/2019) ; variantes `*-invalides`, `*-concessions-*` |
| Export complet | `GET /api/explore/v2.1/catalog/datasets/{jeu}/exports/parquet` (aussi `csv`, `json`) : 85 Mo en Parquet, ~140 s depuis le cloud |
| Pagination de `/records` | limitée (offset + limit ≤ 10 000) : **passer par `/exports`** |
| Fraîcheur | mise à jour quotidienne (dernière `datepublicationdonnees` : 2026-09-21 ; jeu modifié le 2026-09-22) |

La source prévue par la spec (DECP consolidées sur data.gouv.fr) n'est **pas joignable depuis l'environnement cloud** (`www.data.gouv.fr` coupe la connexion). Correspondance exacte entre les deux publications non vérifiée (voir « Écarts »).

## Volumétrie

| Jeu | Lignes | Marchés distincts (`acheteur_id` + `id`) | Années de notification |
|---|---:|---:|---|
| format 2022 | 708 152 | 611 894 | essentiellement 2024 → aujourd'hui (2024 : 232 k lignes, 2025 : 254 k, 2026 : 123 k) |
| format 2019 (v3) | 702 901 | non mesuré | 2018 → 2023 (2019 : 128 k, 2020 : 138 k, 2021 : 165 k, 2022 : 162 k, 2023 : 91 k) |

Les deux formats se complètent : **l'historique 2018-2023 n'existe qu'au format 2019**. Montant total brut (format 2022, avant dédoublonnage) : 1 052 Md€, chiffre non significatif tant que les lignes ne sont pas dédoublonnées.

## Structure (format 2022)

54 colonnes, dont : `id`, `acheteur_id` (SIRET de l'acheteur), `titulaire_id_1..3` + `titulaire_typeidentifiant_1..3`, `montant` (double), `datenotification`, `nature`, `procedure`, `techniques` (accord-cadre…), `idaccordcadre`, `dureemois`, `codecpv`, `objet`, `lieuexecution_*`, modifications (`idmodification`, `montantmodification`, `idtitulairemodification`…), sous-traitance (`idactesoustraitance`, `montantactesoustraitance`, `idsoustraitant`…), `source` (plateforme de publication).

**Absents du format 2022** : dénomination de l'acheteur et des titulaires (le format 2019 a `acheteur_nom`, `titulaire_denominationsociale_*`). Noms à lire via l'API Sirene, conformément à l'ADR 0004.

## Pièges constatés

1. **Lignes aplaties** : une ligne par combinaison marché × titulaire × modification × acte de sous-traitance. Le `montant` du marché est **répété** sur chaque ligne de modification ou de sous-traitance : sommer la colonne brute compte plusieurs fois le même marché.
2. **Sentinelle `CDL`** pour « champ vide » dans les colonnes de co-titulaires, modifications, sous-traitance et accord-cadre (ex. `titulaire_id_2 = 'CDL'` sur 654 608 lignes). À traiter comme nul.
3. **Pas de clé unique naturelle** : `(acheteur_id, id)` → plusieurs lignes ; `(acheteur_id, id, titulaire_id_1)` : 679 786 valeurs pour 708 152 lignes. Seule la ligne complète (y compris `source`) est unique. Le `source_record_id` devra être composé : acheteur, id, titulaire, plus l'identifiant de modification le cas échéant.
4. **Même marché publié par plusieurs plateformes** : 621 marchés présents sous plusieurs `source` (profils d'acheteur différents).
5. **Plusieurs titulaires, montants distincts** pour un même `id` (21 482 marchés à montants multiples) : lots ou accord-cadre multi-attributaire, un montant par titulaire.
6. **Accords-cadres** : 261 308 lignes `Accord-cadre` (37 %) ; leur montant est un **maximum** → borne plafond (SPEC §6.4).
7. **Montants extrêmes** : 1 715 lignes à ≤ 1 € et 2 938 à < 100 € ; 101 lignes ≥ 1 Md€ (maximum 3 Md€ : accords-cadres ministériels de télécommunications, plausibles comme plafond). Médiane 120 k€, 99e centile 16 M€.
8. **Identifiants titulaires** : SIRET à 99,8 % (706 397 lignes), puis TVA (1 416), HORS-UE (172), IREP (159), RIDET, TAHITI. 194 « SIRET » à 17 caractères (saisie erronée), TVA de longueurs variables.
9. **Format 2019 : SIRET du titulaire typé nombre** dans l'API (`titulaire_id_1: 38371167801308`) : les zéros de tête sont perdus. Recompléter à 14 chiffres puis contrôler la clé de Luhn ; `titulaire_typeidentifiant_1` contient aussi des valeurs parasites (SIRET dans le champ type).
10. **Dates de notification aberrantes** : quelques marchés de 2010-2017 dans le format 2022.

## Données utiles pour les autres canaux

`acheteur_id` (SIRET) identifie le **payeur** ; son nom et sa catégorie viendront de l'API Sirene, et éventuellement de l'annuaire de l'administration (voir `pistes.md`).
