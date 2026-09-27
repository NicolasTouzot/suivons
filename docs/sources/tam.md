# TAM — Transparency Award Module (canal `AIDE_ETAT`)

> Spike du 2026-09-27 (lot 0, étape 9).

## Accès

| | |
|---|---|
| Producteur | Commission européenne, DG Concurrence ; données saisies par les autorités d'octroi des États membres |
| Interface | `https://webgate.ec.europa.eu/competition/transparency/public` (formulaire web, français disponible) |
| API | **aucune API publique** |
| Export | après une recherche (POST `…/public/search/results`, jeton `CSRFTOKEN` + cookie de session) : `GET …/public/search/export?format=CSV` (ou `XLSX`) |
| Limite d'export direct | **≈ 1 000 résultats** : 824 lignes exportées directement ; à partir de ~1 600, l'export exige nom et e-mail et le fichier est envoyé par courriel |
| Filtres utiles | `countries=CountryFRA`, `dateGrantedFrom` / `dateGrantedTo` (jj/mm/aaaa), `beneficiaryNationalId`, `grantingAuthorityRegions`, `aidInstruments`… |
| Licence / conditions | non trouvées sur l'interface : **à vérifier** avant toute collecte automatisée (réutilisation des données de la Commission, décision 2011/833/UE en principe) |

## Volumétrie (France)

| Période d'octroi | Résultats |
|---|---:|
| 2016 → 2023 | ~77 000 |
| 2023 | ~16 000 |
| 1er semestre 2024 | ~21 000 |
| janvier 2025 | 407 |

Ordre de grandeur : quelques dizaines de milliers d'aides par an, avec des pics (mesures fiscales, fin d'année).

## Format de l'export CSV

UTF-8 avec BOM, séparateur virgule, en-têtes en français. Colonnes : `Pays`, `Benéficiaire dans un autre État membre`, `Intitulé de la mesure d'aide` (+ EN), `Numéro SA`, `Numéro de référence` (`TM-…`, **unique**), `ID national`, `Nom du bénéficiaire` (+ EN), `Type de bénéficiaire` (PME / grandes entreprises), `Région`, `Secteur (NACE)`, `Instrument d'aide` (+ EN), `Objectif de l'aide` (+ EN), `Montant nominal, exprimé en montant total`, `Élément d'aide, exprimé en montant total`, `Monnaie`, `Date d'octroi` (jj/mm/aaaa), `Nom de l'autorité chargée de l'octroi de l'aide` (+ EN), `Date de publication`, `Entité en charge`, `Intermédiaires financiers`, `Pays tiers hors UE`.

## Pièges constatés (échantillon de janvier 2025, 407 lignes)

1. **Identifiant national** : SIREN (9 chiffres) pour 371 lignes (91 %), SIRET (14 chiffres) pour 28 ; le reste est formaté avec des espaces ou des espaces insécables (`452 592 710`, `930 462 569 00011`). Normaliser avant contrôle de Luhn.
2. **Deux montants** : l'`Élément d'aide` est toujours renseigné, le `Montant nominal` seulement dans 145 cas sur 407. Pour un prêt, les deux diffèrent fortement : choix du montant à fixer dans la méthodologie (SPEC §6.4).
3. **Montants en tranches** pour certaines aides fiscales : `> 999,999 - 999,999` (ex. `> 1,000,000 - 2,000,000`), avec des séparateurs de milliers anglais → borne ferme = bas de tranche, borne plafond = haut de tranche.
4. **Montants décimaux** (`99999.99`) : arrondir à l'euro (SPEC §8).
5. **Seuil de publication** : les aides en dessous du seuil (100 k€ dans le cas général) sont absentes (SPEC §4). Le registre de minimis (voir `pistes.md`) couvre une partie des petites aides depuis 2026.
6. Autorités d'octroi en texte libre (« Ministère de l'économie et des finances », « Ministère de l'économie ») : pas d'identifiant du payeur. Rattachement du payeur par table de correspondance.

## Conséquences pour l'ingestion

- Collecte par **fenêtres de dates adaptatives** (découper tant qu'une fenêtre dépasse ~1 000 résultats ; un jour trop chargé se découpe encore par région ou par instrument).
- Scraping d'un formulaire web : fragile (jeton CSRF, session, changement d'interface). Tests WireMock sur des pages enregistrées et alerte en cas d'échec.
- Clé : `Numéro de référence` (`TM-…`).
