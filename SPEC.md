# SPEC — Suivre Notre Argent (nom de travail) — MVP

> Statut : v0.3 — spécification du MVP (référentiel « API d'abord », ADR 0004 ; sources confirmées par le spike du lot 0, `docs/sources/`)
> Nom et domaine non arrêtés (voir §14 Décisions ouvertes)
> Ce document est la source de vérité fonctionnelle et technique. Toute évolution de périmètre passe par une mise à jour de ce fichier.

---

## 1. Vision

Permettre à n'importe qui (citoyen, journaliste, élu, chercheur) de savoir **combien d'argent public une entreprise a reçu**, quel que soit le canal (commande publique, aides d'État, fonds européens), avec **chaque euro relié à sa source officielle**.

Trois principes non négociables :

1. **Sourcé** : aucun montant affiché sans lien vers le jeu de données d'origine.
2. **Honnête sur les limites** : on affiche des bornes (ferme / plafond) et une couverture explicite, jamais un total trompeur.
3. **Lisible** : très visuel, moderne, pas administratif.

## 2. Périmètre du MVP

### Dans le MVP

| # | Bloc | Résumé |
|---|------|--------|
| F1 | Recherche d'entreprise | Nom, SIREN, SIRET, autocomplétion tolérante aux fautes |
| F2 | Fiche entreprise | Montant tracé avec bornes, répartition par canal, top payeurs (sankey), chronologie, couverture |
| F3 | Détail et traçabilité des flux | Liste filtrable, lien source ligne à ligne, montants aberrants signalés |
| F4 | Méthodologie et transparence | Page méthodologie, état des sources, signalement d'erreur |
| F5 | Accueil | Chiffres clés et classements pédagogiques |

### Hors MVP (V2 et au-delà)

Filtres avancés de recherche, export CSV, partage social, vue payeur, vue groupe (maison-mère / filiales), source PAC, subventions SCDL, ADEME, France 2030, **registre public des aides de minimis** (canal `AIDE_ETAT`, SIREN direct, octrois depuis 2026 ; licence à vérifier), détection de signaux, assistant en langage naturel.

## 3. Glossaire

| Terme | Définition |
|-------|-----------|
| **Flux** | Un versement ou engagement d'argent public vers un bénéficiaire, issu d'un enregistrement source. |
| **Canal** | Catégorie du flux. MVP : `MARCHE` (commande publique), `AIDE_ETAT` (aides d'État déclarées), `FONDS_UE` (fonds de cohésion). |
| **Payeur** | Entité publique à l'origine du flux (acheteur, ministère, opérateur, collectivité, autorité de gestion UE). |
| **Bénéficiaire** | Entreprise qui reçoit le flux, identifiée par son SIREN. |
| **Montant ferme** | Montant qu'on peut raisonnablement considérer comme engagé (borne basse). |
| **Montant plafond** | Montant maximal possible (accords-cadres, co-titulaires) (borne haute). |
| **Couverture** | Description des canaux observés et des canaux structurellement invisibles pour une entreprise. |
| **Provenance** | Ensemble des métadonnées reliant un flux à sa source : jeu de données, identifiant d'enregistrement, URL, date d'extraction. |
| **Rattachement** | Association d'un enregistrement source à un SIREN (direct, résolu automatiquement ou non résolu). |

## 4. Sources du MVP

> Endpoints, formats, volumétries et pièges **constatés au spike du lot 0** : `docs/sources/` (une note par source). Les URL et paramètres restent de la configuration, jamais du code en dur.

| Source | Rôle | Contenu utile | Identifiant bénéficiaire | Fréquence visée |
|--------|------|---------------|--------------------------|-----------------|
| **API Sirene 3.11** (INSEE, `api.insee.fr/api-sirene/3.11`) | Référentiel entreprises, **interrogé à la demande** (pas de copie du stock) | Dénomination, NAF, catégorie juridique, catégorie d'entreprise, siège, état, statut de diffusion | SIREN / SIRET | Mise à jour quotidienne par l'INSEE ; rafraîchissement de notre référentiel minimal : hebdomadaire |
| **DECP consolidées par la DAJ** (`data.economie.gouv.fr`, jeux `decp-v3-marches-valides` pour 2018-2023 et `decp-2022-marches-valides` à partir de 2024, export Parquet) | Canal `MARCHE` | Acheteur (SIRET), titulaires, objet, montant, technique (accord-cadre ou non), date de notification, modifications | SIRET du titulaire (99,8 % des lignes) | Hebdomadaire (source mise à jour chaque jour) |
| **TAM** (Transparency Award Module, Commission européenne) : **pas d'API**, recherche publique puis export CSV, plafonné à ~1 000 résultats par recherche | Canal `AIDE_ETAT` | Autorité d'octroi, bénéficiaire, élément d'aide et montant nominal, instrument, date, mesure (numéro SA) | SIREN ou SIRET (quasi systématique, formats hétérogènes) | Mensuelle |
| **Kohesio** (Commission européenne) : API JSON de `kohesio.ec.europa.eu` (non documentée) | Canal `FONDS_UE` | Projet, bénéficiaires, montant UE programmé, programme, dates | **Aucun** : nom du bénéficiaire seul | Mensuelle (collecte incrémentale) |

### Services d'appui (hors canaux)

| Service | Rôle | Contraintes |
|---------|------|-------------|
| **API Sirene 3.11** | Existence et statut des SIREN, identité détaillée de la fiche (lecture en direct) | Clé API (en-tête `X-INSEE-Api-Key-Integration`, secret `INSEE_API_KEY`) ; **30 req/min et 2 000 req/h** ; requêtes multicritères jusqu'à 1 000 unités par appel |
| **API Recherche d'entreprises** (DINUM, `recherche-entreprises.api.gouv.fr`) | Candidats pour le rattachement par nom ; repli de la recherche pour les entreprises sans flux | Sans clé ; **7 req/s par IP, 30 req/s par ASN** (`429` + `Retry-After`) ; détection des robots côté fournisseur (bloque l'environnement cloud de développement) : à vérifier depuis l'hébergement cible |

Principe (ADR 0004) : **une donnée disponible par API n'est pas recopiée en base**, sauf les champs strictement nécessaires aux calculs, à la recherche et au respect du RGPD (§6.3).

### Pièges connus à traiter

- **DECP** : deux formats à fusionner (2019 et 2022) ; lignes **aplaties** (une ligne par marché × titulaire × modification × acte de sous-traitance), le montant du marché étant répété sur chaque ligne ; valeur sentinelle `CDL` pour « vide » ; pas de clé naturelle unique ; un même marché publié par plusieurs plateformes ; montants aberrants (≤ 1 €, ≥ 1 Md€) ; accords-cadres exprimés en plafond (37 % des lignes) ; SIRET mal saisi, et zéros de tête perdus dans le format 2019 (SIRET typé nombre). Règles de traitement : §6.4.
- **TAM** : seuil de publication (≥ 100 k€ dans le cas général) : les aides en dessous sont invisibles ; montants en tranches pour certaines aides fiscales ; deux montants (élément d'aide, nominal) ; identifiant national avec espaces ou espaces insécables ; export limité à ~1 000 résultats par recherche : collecte par **fenêtres de dates adaptatives** ; formulaire web (jeton CSRF, session) : fragile, surveillé. Conditions de réutilisation à vérifier avant le lot 5.
- **Kohesio** : aucun identifiant national, rattachement par nom uniquement : forte dépendance au moteur de réconciliation ; montant porté par le projet et non par bénéficiaire ; montant **programmé**, pas versé ; bénéficiaires disponibles seulement dans le détail de chaque projet (~1 s par appel, 63 000 projets français) : collecte incrémentale ; API non documentée, surveillée. Conditions de réutilisation à vérifier avant le lot 5.
- **SIRENE** : les unités en **diffusion partielle** (`statutDiffusion = P`) et les **entrepreneurs individuels** (catégorie juridique `1000`, ~5 % des titulaires DECP, même en diffusion `O`) ne doivent jamais être exposés nominativement ; le statut est à relire à chaque rafraîchissement (oppositions possibles à tout moment). Quotas API bas : toute lecture en direct passe par un cache et un disjoncteur.

## 5. Spécifications fonctionnelles

### F1 — Recherche d'entreprise

**User story** : en tant que visiteur, je tape un nom, un SIREN ou un SIRET et j'accède à la fiche de l'entreprise.

- Autocomplétion dès 3 caractères, tolérante aux fautes et aux accents (pg_trgm), sur le **référentiel local des bénéficiaires** (entreprises ayant au moins un flux, §6.3).
- Si l'entreprise cherchée n'a aucun flux tracé : repli sur l'API Recherche d'entreprises, résultat affiché avec le badge « aucun flux tracé ».
- Un SIREN (9 chiffres) ou un SIRET (14 chiffres) valide redirige directement vers la fiche.
- Chaque résultat affiche : dénomination, commune du siège, libellé NAF, **total tracé (borne ferme)**, badge « aucun flux tracé » le cas échéant.
- Tri : pertinence textuelle, puis total tracé décroissant.
- Les entreprises en diffusion partielle et les personnes physiques sont exclues (§11).

**Critères d'acceptation**

- Recherche « societe generale » trouve « SOCIÉTÉ GÉNÉRALE ».
- Faute d'une lettre tolérée sur un nom de plus de 6 caractères.
- p95 < 200 ms sur le référentiel local complet (le repli externe est hors de ce budget et chargé en complément).

### F2 — Fiche entreprise

**User story** : en tant que visiteur, je comprends en 10 secondes combien cette entreprise a reçu d'argent public, de qui, et avec quelle fiabilité.

Contenu, dans l'ordre d'affichage :

1. **En-tête identité** : dénomination, SIREN, NAF, commune, état (active / cessée) depuis le référentiel local ; catégorie d'entreprise, catégorie juridique, adresse, date de création **lues en direct** via l'API Sirene (cache 24 h) ; lien vers l'Annuaire des entreprises. Si l'API est indisponible ou le quota atteint, la fiche s'affiche avec l'identité minimale, sans erreur.
2. **Montant tracé** : chiffre principal = borne ferme ; barre de fourchette ferme → plafond ; période couverte (première et dernière année).
3. **Répartition par canal** : montant et nombre de flux par canal, couleur fixe par canal.
4. **Top payeurs** : sankey payeurs → entreprise (10 premiers, le reste agrégé en « Autres »), avec alternative tableau.
5. **Chronologie** : barres empilées par année et par canal.
6. **Couverture** : liste des canaux observés vs non observables (§6.4), sous forme de jauge qualitative, jamais de pourcentage.
7. **Accès au détail** : lien vers F3.

**Critères d'acceptation**

- Chaque chiffre affiché est recalculable à partir de la liste F3.
- Une entreprise sans flux (absente du référentiel local) affiche une fiche valide, construite depuis l'API Sirene, avec message explicite et couverture ; si elle est non diffusible ou personne physique, aucune donnée nominative n'est affichée.
- p95 < 300 ms pour les montants (lecture depuis le mart) ; l'identité détaillée a son propre budget (p95 < 1 s, cache compris) et ne bloque jamais l'affichage des montants.

### F3 — Détail et traçabilité des flux

**User story** : en tant que journaliste, je vérifie chaque ligne et remonte à la source officielle.

- Tableau paginé : date, canal, payeur, objet, montant ferme, montant plafond, qualité, **source**.
- Filtres : canal, année, payeur. Tri : date, montant.
- Colonne source : nom du jeu de données, date d'extraction, lien direct vers l'enregistrement ou le jeu de données.
- Les flux `ABERRANT` sont affichés avec un badge et une explication, **exclus des totaux**.
- Les flux à rattachement automatique (par nom) affichent leur niveau de confiance.

**Critères d'acceptation**

- 100 % des lignes ont une provenance complète (contrainte base).
- La somme des lignes non aberrantes = les totaux de F2.

### F4 — Méthodologie et transparence

- **Page méthodologie** (contenu éditorial versionné dans le repo, Markdown) : sources, définitions, règles de calcul des bornes, règles de rattachement, limites, canaux invisibles.
- **Page état des sources** : pour chaque source, dernière ingestion réussie, version du jeu de données, volumétrie chargée, rejets, taux de rattachement.
- **Signalement d'erreur** : formulaire accessible depuis chaque fiche et chaque ligne de flux (type d'erreur, commentaire, email optionnel). Stocké en base, anti-spam (honeypot et rate limiting), aucune donnée personnelle obligatoire.

### F5 — Accueil

- Accroche : champ de recherche proéminent.
- Chiffres clés : total tracé par canal, nombre d'entreprises bénéficiaires, date de dernière mise à jour.
- Classements : top 10 bénéficiaires par canal et par année, **systématiquement accompagnés** d'un encart pédagogique (ce que le chiffre dit et ne dit pas) et d'un lien vers la méthodologie.
- Pas de formulation accusatoire (§11.3).

## 6. Modèle de données

PostgreSQL, organisé en **quatre schémas**. Migrations exclusivement via Flyway (module `db`).

```
raw   → données brutes telles que reçues (rejouables)
core  → modèle normalisé (entreprises, payeurs, flux)
mart  → agrégats pré-calculés servis par l'API
ops   → exploitation (sources, exécutions, rejets, signalements)
```

Le référentiel entreprises n'est **pas** une copie de SIRENE : seules les entreprises bénéficiaires d'au moins un flux y figurent, avec les champs minimaux listés au §6.3 (ADR 0004).

### 6.1 Schéma `ops`

| Table | Colonnes clés |
|-------|---------------|
| `ops.source` | `code` (PK : `SIRENE`, `RECHERCHE_ENTREPRISES`, `DECP`, `TAM`, `KOHESIO`), `libelle`, `producteur`, `licence`, `url_reference`, `frequence` |
| `ops.ingestion_run` | `id`, `source_code`, `version_source` (date ou hash du jeu), `debut`, `fin`, `statut` (`EN_COURS`, `SUCCES`, `ECHEC`), `lus`, `charges`, `rejetes`, `checksum_fichier` |
| `ops.rejet` | `run_id`, `source_record_id`, `motif`, `payload` (jsonb) |
| `ops.batch_*` | Tables techniques de Spring Batch (reprise des jobs), DDL officiel préfixé `ops.` (décision du 2026-09-27) |
| `ops.signalement` | `id`, `siren`, `flux_id` (nullable), `type`, `commentaire`, `email` (nullable), `cree_le`, `statut` |

### 6.2 Schéma `raw`

Une table par source de flux (`DECP`, `TAM`, `KOHESIO`) : `raw.<source>_record(run_id, source_record_id, payload jsonb, checksum, recu_le)`. Pas de table `raw` pour les API d'appui (SIRENE, Recherche d'entreprises) : elles restent la référence et ne sont pas archivées.
Objectif : pouvoir **rejouer la transformation** sans retélécharger, et prouver ce qu'on a reçu.

### 6.3 Schéma `core`

**`core.entreprise`** — référentiel **minimal**, limité aux bénéficiaires d'au moins un flux : `siren` (PK), `denomination`, `naf_code`, `naf_nomenclature`, `commune_siege`, `departement_siege`, `etat` (`ACTIVE`, `CESSEE`), `diffusible` (bool, faux si `statutDiffusionUniteLegale = P`), `personne_physique` (bool, vrai si catégorie juridique `1000`), `rafraichi_le`.

| Champ | Raison du stockage local |
|-------|--------------------------|
| `siren`, `denomination` | Recherche floue (pg_trgm) |
| `naf_code`, `commune_siege`, `departement_siege` | Affichés dans chaque résultat de recherche ; bonus de rattachement |
| `diffusible`, `personne_physique` | Obligation RGPD : filtrage avant tout affichage et dans les agrégats, indépendamment de la disponibilité de l'API |
| `etat` | Identité minimale en mode dégradé |

Tout autre champ d'identité est lu en direct via l'API Sirene. Une entreprise dont tous les flux disparaissent est retirée du référentiel au rafraîchissement suivant.

**`core.naf`** : `nomenclature` (`NAFRev2`, `NAF2025`), `code`, `libelle` ; PK `(nomenclature, code)`. Nomenclature publique statique (~730 sous-classes par édition), chargée depuis le fichier publié par l'INSEE (contrôle du checksum) ; conservée localement car nécessaire à chaque résultat de recherche. La NAF 2025 remplace la NAF rév. 2 au 1er janvier 2027 : `core.entreprise` porte `naf_code` et `naf_nomenclature`.

**`core.payeur`** : `id`, `identifiant` (SIRET/SIREN ou identifiant UE), `nom`, `type` (`ETAT`, `OPERATEUR`, `COLLECTIVITE`, `HOPITAL`, `UE`, `AUTRE`).

**`core.flux`** (partitionnée par `annee`) :

| Colonne | Description |
|---------|-------------|
| `id` | PK |
| `canal` | `MARCHE`, `AIDE_ETAT`, `FONDS_UE` |
| `beneficiaire_siren` | FK `core.entreprise` (nullable si non résolu) |
| `beneficiaire_nom_source` | Nom tel que présent dans la source |
| `payeur_id` | FK `core.payeur` |
| `objet` | Objet du marché, de l'aide ou du projet |
| `date_flux`, `annee` | Date de référence (notification, octroi, début de projet) |
| `montant_ferme` | Borne basse (nullable) |
| `montant_plafond` | Borne haute |
| `nature_montant` | `FERME`, `PLAFOND`, `PARTAGE`, `INCONNU` |
| `qualite` | `OK`, `ABERRANT` |
| `rattachement` | `SIREN_SOURCE`, `RESOLU_AUTO`, `NON_RESOLU` |
| `confiance_rattachement` | 0..1 (1 si `SIREN_SOURCE`) |
| `source_code`, `source_record_id` | **NOT NULL**, unique ensemble |
| `source_url` | **NOT NULL** : lien vers l'enregistrement ou le jeu de données |
| `run_id`, `extrait_le` | **NOT NULL** |

Contrainte d'unicité `(source_code, source_record_id)` : garantit l'**idempotence** des ingestions (upsert). PostgreSQL imposant la clé de partition dans toute contrainte d'unicité, la base porte `UNIQUE (source_code, source_record_id, annee)` ; l'unicité sans l'année est garantie par `ingestion-core` (un flux qui change d'année remplace sa ligne), et testée. Partitions annuelles 2010 → 2030 et une partition par défaut pour les dates hors plage.

### 6.4 Règles de calcul

**Bornes ferme / plafond**

| Cas | `montant_ferme` | `montant_plafond` | `nature_montant` |
|-----|-----------------|-------------------|------------------|
| Marché ordinaire, titulaire unique | montant | montant | `FERME` |
| Accord-cadre | null (0 dans les agrégats) | montant maximum | `PLAFOND` |
| Co-titulaires (montant non ventilé) | null (0 dans les agrégats) | montant total, attribué à chaque co-titulaire | `PARTAGE` |
| Marché modifié par avenant | dernier montant connu | dernier montant connu | selon le cas |
| Aide TAM en fourchette (tranche) | bas de la tranche | haut de la tranche | `PLAFOND` |
| Aide TAM à montant unique | élément d'aide | élément d'aide | `FERME` |
| Projet Kohesio, bénéficiaire unique | montant UE du projet | montant UE du projet | `FERME` |
| Projet Kohesio, plusieurs bénéficiaires (montant non ventilé) | null (0 dans les agrégats) | montant UE du projet, attribué à chaque bénéficiaire | `PARTAGE` |

Conséquence assumée : la somme des bornes plafond sur plusieurs entreprises peut dépasser l'argent réellement dépensé. C'est documenté en méthodologie ; aucun total global n'additionne des plafonds.

**Montant de référence TAM** : l'**élément d'aide** (équivalent-subvention, toujours renseigné). Le montant nominal, quand il existe (prêts, garanties), est conservé et affiché en complément dans le détail du flux (F3), jamais agrégé.

**Montant Kohesio** : soutien UE **programmé** pour le projet (et non versé), qualifié comme tel dans l'interface et la méthodologie.

**Déduplication DECP** : un marché est identifié par `uid` (SIRET acheteur + identifiant interne) ; **un flux par `(uid, titulaire)`**, avec `source_record_id = <SIRET acheteur>|<id>|<identifiant titulaire>` pour les deux formats (2019 et 2022). Règles :

- la valeur `CDL` vaut « vide » ;
- montant = dernier montant connu après modifications (`montantmodification` de la modification la plus récente, sinon `montant`) ;
- les lignes d'actes de sous-traitance n'apportent aucun montant au titulaire (la sous-traitance n'est pas observée, §6.4 Couverture) ;
- un même `(uid, titulaire)` publié par plusieurs plateformes est fusionné (dernière `datepublicationdonnees`) ;
- co-titulaires d'un groupement (`titulaire_id_2..3` renseignés) → `PARTAGE` ; titulaires distincts à montants distincts pour un même `uid` (lots, accord-cadre multi-attributaire) → un flux chacun, avec son propre montant ;
- identifiant titulaire recomplété à 14 chiffres (format 2019) puis contrôlé (Luhn) avant rattachement.

**Montants aberrants** (`qualite = ABERRANT`) : montant ≤ 1 €, ou montant > seuil configurable (défaut 1 Md€, sauf liste blanche). Ils restent visibles en F3, **exclus de tous les agrégats**. Seuils à calibrer au lot 2.

**Couverture** : catalogue statique (table `ops.canal_couverture`) listant, pour chaque canal connu, s'il est observé dans le MVP :

- Observés : commande publique (DECP), aides d'État déclarées ≥ seuil TAM, fonds de cohésion UE (Kohesio).
- Non observables : dépenses fiscales (CIR, crédits d'impôt), allègements de cotisations, prêts et garanties (Bpifrance, PGE), subventions non publiées, sous-traitance de marchés, aides sous les seuils de publication.

### 6.5 Réconciliation (rattachement au SIREN)

Ordre d'application :

1. **SIREN/SIRET présent dans la source** : SIRET → SIREN (9 premiers chiffres), contrôle de Luhn, existence vérifiée dans `core.entreprise` ou, à défaut, via l'API Sirene (requêtes groupées, jusqu'à 1 000 SIREN par appel) puis ajout au référentiel minimal → `SIREN_SOURCE`, confiance 1.
2. **Correspondance par nom** : candidats obtenus via l'API Recherche d'entreprises (et le référentiel local) ; normalisation (majuscules, accents, formes juridiques retirées), score de similarité calculé chez nous, bonus si commune ou département concordant → `RESOLU_AUTO` si confiance ≥ seuil (défaut 0,9), puis ajout au référentiel minimal.
3. Sinon → `NON_RESOLU` : le flux est conservé, **jamais rattaché**, et compté dans les statistiques de la page état des sources.

Les appels externes respectent les quotas (file d'attente, reprise). Pour garantir l'idempotence, un rattachement déjà établi pour un `(source_code, source_record_id)` est réutilisé lors d'un rejeu ; il n'est recalculé que sur demande explicite.

Règle : on préfère ne pas rattacher plutôt que de rattacher à tort. Le seuil est un paramètre, calibré sur un échantillon étiqueté manuellement (lot 5).

### 6.6 Schéma `mart`

Vues matérialisées rafraîchies en fin de chaque ingestion réussie (`REFRESH MATERIALIZED VIEW CONCURRENTLY`) :

- `mart.entreprise_synthese` : totaux ferme et plafond, par canal, nombre de flux, première et dernière année.
- `mart.entreprise_payeur` : montants par couple entreprise × payeur.
- `mart.entreprise_annee` : montants par entreprise × année × canal.
- `mart.classement` : top N par canal × année.
- `mart.chiffres_cles` : agrégats de la page d'accueil.

Seuls les flux `qualite = OK` et `rattachement IN (SIREN_SOURCE, RESOLU_AUTO)` entrent dans les agrégats, et seulement pour des entreprises `diffusible AND NOT personne_physique`.

## 7. Architecture

### 7.1 Vue d'ensemble

```
             ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
 Sources →   │ingestion-    │  │ingestion-    │  │ingestion-    │  │ingestion-    │
 open data   │sirene        │  │decp          │  │tam           │  │kohesio       │
             └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
                    │ s'appuient sur ingestion-core + reconciliation        │
                    ▼                                                        ▼
             ┌─────────────────────────────────────────────────────────────────────┐
             │ PostgreSQL : raw → core → mart  (+ ops)       schéma géré par db    │
             └──────────────────────────────────┬──────────────────────────────────┘
                                                │ lecture seule (mart, core)
                                                ▼
                                    ┌──────────────────────┐
                                    │ api (Spring Boot)     │  ← contrat : contract/openapi.yaml
                                    └──────────┬───────────┘
                                               │ REST JSON
                                               ▼
                                    ┌──────────────────────┐
                                    │ front (Angular)       │  ← client généré depuis le contrat
                                    └──────────────────────┘
```

### 7.2 Modules (monorepo Gradle multi-projets + projet Angular)

| Module | Type | Responsabilité | Dépend de |
|--------|------|----------------|-----------|
| `db` | Lib | Migrations Flyway, génération du code jOOQ | — |
| `domain` | Lib | Types métier partagés (canal, bornes, règles de calcul pures), sans dépendance Spring ni SQL | — |
| `ingestion-core` | Lib | Téléchargement avec checksum et cache, écriture `raw`, suivi `ops.ingestion_run`, gestion des rejets, provenance, upsert `core.flux`, rafraîchissement du mart | `db`, `domain` |
| `referentiel-client` | Lib | Clients HTTP de l'API Sirene et de l'API Recherche d'entreprises : quotas, cache, disjoncteur, mode dégradé | `domain` |
| `reconciliation` | Lib | Normalisation des identifiants et des noms, rattachement SIREN, score de confiance | `db`, `domain`, `referentiel-client` |
| `ingestion-sirene` | App Spring Batch | Rafraîchissement du référentiel minimal via l'API Sirene (statut de diffusion, état, dénomination) ; chargement de `core.naf` | `ingestion-core`, `referentiel-client` |
| `ingestion-decp` | App Spring Batch | Canal `MARCHE` | `ingestion-core`, `reconciliation` |
| `ingestion-tam` | App Spring Batch | Canal `AIDE_ETAT` | `ingestion-core`, `reconciliation` |
| `ingestion-kohesio` | App Spring Batch | Canal `FONDS_UE` | `ingestion-core`, `reconciliation` |
| `contract` | Spec | `openapi.yaml`, source de vérité de l'API | — |
| `api` | App Spring Boot | Exposition REST en lecture (+ signalements) ; identité détaillée et repli de recherche via `referentiel-client` | `db`, `domain`, `contract`, `referentiel-client` |
| `front` | App Angular | Interface | `contract` (client généré) |

**Règles de dépendance** (vérifiées par ArchUnit en TU) :

- Un module `ingestion-<source>` ne dépend jamais d'un autre module `ingestion-<source>`.
- `api` ne dépend d'aucun module `ingestion-*` ni de `reconciliation`.
- `domain` ne dépend ni de Spring, ni de jOOQ.
- `referentiel-client` ne dépend ni de `db`, ni de jOOQ : il n'écrit jamais en base.
- Seul `ingestion-core` écrit dans `core.flux` ; l'`api` n'écrit que dans `ops.signalement`.

### 7.3 Pipeline d'ingestion (commun à toutes les sources)

```
extract   → téléchargement, checksum ; si checksum identique au dernier run réussi : arrêt (rien à faire)
load_raw  → écriture brute dans raw.<source>_record, rattachée au run
transform → mapping source → flux normalisé, règles §6.4, rejets tracés dans ops.rejet
reconcile → rattachement SIREN (§6.5)
upsert    → core.flux par (source_code, source_record_id)
refresh   → rafraîchissement des vues mart
report    → mise à jour ops.ingestion_run (compteurs, statut)
```

- Chaque source est un **exécutable indépendant** (jar Spring Boot), lancé par un ordonnanceur externe (cron, CronJob Kubernetes). Pas d'ordonnanceur embarqué.
- **Idempotence** : rejouer un run sur la même version de source produit exactement le même état.
- **Échec partiel** : un run en échec ne rafraîchit pas le mart ; l'état précédent reste servi.
- Ajouter une source = ajouter un module `ingestion-<source>` qui implémente les points d'extension d'`ingestion-core`, sans modifier les autres modules.

### 7.4 Stack

| Couche | Choix | Justification |
|--------|-------|---------------|
| Langage back | Java, dernière LTS | Choix projet |
| Framework | Spring Boot (dernière version stable), Spring Batch pour l'ingestion | Choix projet ; Spring Batch apporte reprise, chunks et métadonnées de run |
| Accès données | **jOOQ** (pas de JPA) | Requêtes analytiques, SQL typé, génération depuis le schéma Flyway |
| Base | PostgreSQL (dernière version majeure stable), extensions `pg_trgm`, `unaccent` | Recherche floue, vues matérialisées, partitionnement |
| Migrations | Flyway | Schéma versionné, source unique |
| Contrat API | OpenAPI 3, approche **API-first** ; génération des interfaces Spring et du client Angular | Front et back ne peuvent pas diverger |
| Front | Angular (dernière version stable : composants standalone, signals), **SSR activé** | Fiches entreprises indexables par les moteurs de recherche |
| Data-viz | Apache ECharts (via `ngx-echarts`) | Sankey, barres empilées, thèmes clair et sombre |
| Build | Gradle (back), npm (front) | — |
| Conteneurs | Docker, `docker-compose` pour le dev et les TS | — |
| Observabilité | Spring Actuator, Micrometer, logs JSON structurés | — |

> Les versions exactes sont figées au lot 0 et consignées dans `CLAUDE.md`.

## 8. API

Base : `/api/v1`. Lecture seule sauf les signalements. Pas d'authentification en lecture.

| Méthode | Endpoint | Usage |
|---------|----------|-------|
| GET | `/entreprises?q=&page=&size=` | F1 : recherche et autocomplétion |
| GET | `/entreprises/{siren}` | F2 : synthèse (identité minimale, bornes, répartition par canal, période, couverture) — données locales uniquement |
| GET | `/entreprises/{siren}/identite` | F2 : identité détaillée lue via l'API Sirene (cache 24 h) ; réponse partielle signalée si l'API est indisponible |
| GET | `/entreprises/{siren}/payeurs?limit=` | F2 : top payeurs (sankey) |
| GET | `/entreprises/{siren}/chronologie` | F2 : montants par année × canal |
| GET | `/entreprises/{siren}/flux?canal=&annee=&payeur=&page=&size=&sort=` | F3 : détail des flux avec provenance |
| GET | `/chiffres-cles` | F5 |
| GET | `/classements?canal=&annee=&limit=` | F5 |
| GET | `/sources` | F4 : état des sources |
| GET | `/couverture` | F4 : catalogue des canaux observés et non observables |
| POST | `/signalements` | F4 : signalement d'erreur |

**Conventions**

- Erreurs au format RFC 9457 (Problem Details).
- Pagination : `page`, `size` (max 100), réponse avec `total`.
- Montants en euros, entiers (centimes arrondis), jamais formatés côté API.
- Toute réponse contenant des montants porte `derniereMiseAJour`.
- Cache HTTP : `ETag` et `Cache-Control` (données modifiées uniquement à l'ingestion).
- Rate limiting sur `/entreprises` et `/signalements`.
- L'API se connecte avec un rôle PostgreSQL en **lecture seule**, sauf sur `ops.signalement`.
- Appels aux API externes : timeout court, cache, disjoncteur ; une indisponibilité externe dégrade l'identité ou le repli de recherche, jamais les montants.

## 9. Design

### 9.1 Intention

**Éditorial et data-viz, pas administratif.** Références d'esprit : Our World in Data, The Pudding, les data-stories des grands médias. Le chiffre est le héros ; la source est toujours à un clic.

À éviter : look formulaire ou portail d'État, tableaux denses en première lecture, jargon administratif, pictogrammes décoratifs.

### 9.2 Identité visuelle

- **Typographie** : une serif éditoriale pour les titres et les grands chiffres, une sans-serif très lisible pour l'interface ; **chiffres tabulaires** partout où l'on compare des montants.
- **Couleurs** : fond neutre (clair ou sombre), un accent de marque unique, et **une couleur fixe par canal**, identique dans tous les graphiques et badges.
- **Design tokens** (couleurs, espacements, rayons, typographie) centralisés, avec thème clair et thème sombre.
- **Mise en forme des montants** : compacte à l'affichage (« 12,4 M€ »), valeur exacte au survol ou au focus.
- **Mouvement** : animations sobres à l'apparition des chiffres et graphiques ; respect de `prefers-reduced-motion`.

### 9.3 Écrans

**Accueil**

```
[ Logo ]                                            [ Méthodologie ] [ Sources ]
─────────────────────────────────────────────────────────────────────────────
        Combien d'argent public a reçu cette entreprise ?
        [ 🔍 Nom, SIREN ou SIRET ................................ ]

   [ XX Md€ marchés ]   [ XX Md€ aides d'État ]   [ XX Md€ fonds UE ]
   Mise à jour : jj/mm/aaaa

   Top bénéficiaires 2025   [Marchés ▾]
   1. ████████████████  Entreprise A   1,2 Md€
   2. ███████████       Entreprise B   0,8 Md€
   ℹ️ Ce que ce classement dit, et ne dit pas →
```

**Fiche entreprise**

```
ENTREPRISE X  ·  SIREN 123 456 789  ·  Ingénierie  ·  Nantes  ·  Active
─────────────────────────────────────────────────────────────────────────────
  48,2 M€  d'argent public tracé (2019 → 2025)
  ├───────────●━━━━━━━━━━━━━━━━━━━━━━━━┤  jusqu'à 71,5 M€ (plafonds)

  [ Marchés 41,0 M€ ] [ Aides d'État 5,2 M€ ] [ Fonds UE 2,0 M€ ]

  Qui paie ?                         Dans le temps
  [ sankey payeurs → entreprise ]    [ barres empilées par année ]

  Couverture : ✅ Marchés  ✅ Aides ≥ 100 k€  ✅ Fonds UE
               ⛔ Crédits d'impôt  ⛔ Allègements  ⛔ Prêts  … (en savoir plus)

  [ Voir les 132 flux et leurs sources → ]           [ Signaler une erreur ]
```

**Détail des flux** : tableau aéré, badge de canal coloré, badge source cliquable, badges « montant plafond », « aberrant », « rattaché par nom (confiance 0,93) ».

### 9.4 Accessibilité et responsive

- Cible WCAG 2.2 AA (RGAA).
- Chaque graphique a une **alternative tableau** accessible ; jamais d'information portée par la couleur seule.
- Mobile-first : la fiche doit être pleinement lisible sur un écran de 375 px.

### 9.5 Ton rédactionnel

Neutre et factuel : « a reçu », « montant attribué », « montant maximal possible ». Interdits dans l'interface : « fraude », « corruption », « suspect », « scandale », ou toute formulation qui prête une intention.

## 10. Stratégie de tests

Tout module est testé aux trois niveaux applicables. **Pas de H2** : les TI tournent sur un vrai PostgreSQL (Testcontainers), à la même version majeure que la production.

| Niveau | Back (Java) | Front (Angular) |
|--------|-------------|-----------------|
| **TU** | JUnit 5, AssertJ. Règles de calcul (`domain`), mappings source → flux, normalisation et scoring de réconciliation, ArchUnit | Vitest et Angular Testing Library : composants, formatage des montants, services |
| **TI** | API externes simulées par **WireMock** (aucun appel réel en CI), y compris quota dépassé et indisponibilité ; Testcontainers PostgreSQL : migrations Flyway, requêtes jOOQ, jobs d'ingestion complets sur fixtures, rafraîchissement mart, endpoints API (MockMvc ou RestAssured) avec **validation des réponses contre `openapi.yaml`** | Tests d'intégration des pages avec client API mocké au niveau HTTP |
| **TS** | — | **Playwright** sur la stack complète (`docker-compose` + jeu de données de démonstration) : parcours recherche → fiche → détail → source ; accessibilité automatisée (axe-core) |

**Tests spécifiques à l'ingestion**

- **Fixtures par source** : échantillons réels réduits, versionnés dans le repo, incluant les cas pièges (§4).
- **Golden files** : pour une fixture donnée, le résultat attendu dans `core.flux` est versionné et comparé.
- **Idempotence** : deux runs successifs sur la même fixture donnent un état identique.
- **Cohérence** : somme des flux F3 = totaux F2, vérifiée en TI.

**Objectifs de couverture** : ≥ 80 % des lignes sur `domain`, `reconciliation`, `ingestion-core` et les mappings ; mutation testing (PIT) sur `domain` et `reconciliation`, score ≥ 70 %.

**CI** : build, TU, TI, ArchUnit, lint front et TS bloquants sur chaque merge request.

## 11. Exigences non fonctionnelles et garde-fous

### 11.1 Performance et volumétrie

- Fiche p95 < 300 ms, recherche p95 < 200 ms, servies depuis le mart et les index trigram.
- Volumétrie (plusieurs dizaines de millions de lignes SIRENE, millions de lignes DECP) à mesurer au lot 0 ; partitionnement de `core.flux` par année.

### 11.2 RGPD et données personnelles

- Les entreprises en **diffusion partielle** SIRENE ne sont jamais affichées nominativement.
- Les **personnes physiques** (entrepreneurs individuels, catégorie juridique `1000`) sont exclues de l'affichage nominatif dans le MVP, **même en diffusion `O`** ; leurs flux restent comptés dans les agrégats globaux anonymes.
- Règle de masquage unique : aucune donnée nominative si `diffusible = faux` **ou** `personne_physique = vrai` (§6.3).
- Signalements : email facultatif, durée de conservation limitée (à fixer), mention d'information.
- Mesure d'audience sans cookie ni traçage individuel.

### 11.3 Intégrité éditoriale

- Aucun montant sans provenance (contrainte `NOT NULL` en base).
- Montants aberrants visibles mais exclus des totaux.
- Page méthodologie versionnée ; tout changement de règle de calcul y est reflété.
- Canal de signalement et de correction ouvert à tous, y compris aux entreprises concernées.
- Respect des licences des sources (Licence Ouverte 2.0, licences UE) : attribution affichée.

### 11.4 Sécurité

- En-têtes de sécurité (CSP, HSTS, etc.), dépendances scannées en CI.
- Rôles PostgreSQL distincts : migration (propriétaire des schémas, seul rôle avec droits DDL, utilisé par la commande de migration dédiée, ADR 0005), ingestion (`suivons_ingestion` : lecture et écriture des données, `ops.source` en lecture), API (`suivons_api` : lecture de `core`, `mart`, `ops.source`, `ops.ingestion_run` ; écriture de `ops.signalement` au lot 6 ; aucun accès à `raw` ni aux rejets). Les rôles applicatifs sont des rôles de groupe sans connexion ; chaque environnement crée ses utilisateurs de connexion, membres de ces rôles.
- Clés d'API externes (`INSEE_API_KEY`) uniquement en secrets d'environnement, jamais dans le dépôt ni dans les logs.

## 12. Découpage en lots

Chaque lot se termine avec : tests TU / TI / TS applicables au vert, documentation à jour, démo possible.

| Lot | Contenu | Définition de terminé |
|-----|---------|-----------------------|
| **0 — Socle** | Monorepo Gradle + Angular, CI, `docker-compose` PostgreSQL, module `db` (Flyway + jOOQ), `contract` (OpenAPI vide), règles ArchUnit, TS Playwright « smoke ». **Spike sources** : endpoints, formats, volumétrie réels des 4 sources, consignés dans `docs/sources/` | `./gradlew build` et pipeline CI verts ; versions figées dans `CLAUDE.md` |
| **1 — Référentiel** | `ingestion-core` (pipeline §7.3), `referentiel-client`, `ingestion-sirene` (rafraîchissement API), schémas `ops`, `raw`, `core.entreprise`, `core.naf` | Rafraîchissement du référentiel minimal prouvé en TI (WireMock), idempotence prouvée, quotas et mode dégradé testés |
| **2 — Marchés** | `ingestion-decp`, règles de bornes et de déduplication, montants aberrants, rattachement par SIRET | Golden files DECP verts ; seuils aberrants calibrés |
| **3 — API** | Mart, endpoints recherche, fiche, payeurs, chronologie, flux, sources | TI API validées contre le contrat ; p95 respectés sur volumétrie réelle |
| **4 — Front cœur** | Design tokens, thèmes, recherche, fiche entreprise, détail des flux, SSR | Parcours TS recherche → fiche → source ; axe-core sans erreur |
| **5 — Aides et fonds UE** | `ingestion-tam`, `ingestion-kohesio`, réconciliation par nom, calibrage du seuil de confiance sur échantillon étiqueté | Taux de faux rattachements mesuré et documenté |
| **6 — Accueil et transparence** | Accueil, classements, méthodologie, état des sources, couverture, signalement | Tous les blocs F1 à F5 livrés |
| **7 — Durcissement** | Performance, accessibilité, sécurité, observabilité, jeu de TS complet | Critères d'acceptation MVP (§13) tous vérifiés |

## 13. Critères d'acceptation du MVP

1. Les quatre sources sont ingérées de bout en bout, de façon idempotente, avec un état visible sur la page sources.
2. Toute entreprise diffusible est trouvable par nom, SIREN ou SIRET.
3. Chaque montant affiché est traçable jusqu'à sa source en deux clics au plus.
4. Les totaux d'une fiche sont égaux à la somme de ses flux non aberrants (vérifié automatiquement).
5. Les bornes ferme / plafond et la couverture sont affichées sur chaque fiche.
6. Aucune personne physique ni entreprise en diffusion partielle n'est exposée nominativement.
7. Performances et accessibilité conformes aux §9.4 et §11.1.
8. Pipeline CI vert avec TU, TI et TS.

## 14. Décisions ouvertes

| Sujet | Options | Échéance |
|-------|---------|----------|
| Nom et domaine | À choisir, disponibilité du domaine à vérifier | Avant mise en ligne |
| Hébergement | Hébergeur français (OVH, Scaleway, Clever Cloud…) ; critère : accès à l'API Recherche d'entreprises (limite par ASN, détection des robots) vérifié depuis l'hébergeur | Lot 7 |
| ~~Licence du code~~ | **Tranché : AGPL-3.0-or-later** (voir `docs/adr/0003-licence-agpl.md`) | Lot 0 ✔ |
| Seuils (aberrants, confiance de rattachement) | Valeurs par défaut du §6, à calibrer | Lots 2 et 5 |
| Durée de conservation des signalements | 6 à 12 mois | Lot 6 |
| Quota API Sirene | Accès public (30 req/min, 2 000 req/h) suffisant ? Sinon demande d'un quota supérieur à l'INSEE | Lot 7 (selon trafic et exploration des robots) |
| ~~Accès à `recherche-entreprises.api.gouv.fr` et `www.data.gouv.fr` depuis l'environnement cloud~~ | **Tranché** : DECP lu sur `data.economie.gouv.fr` (DAJ) ; Recherche d'entreprises conservée, bloquée seulement par la détection des robots sur l'environnement cloud de développement (tests en session locale, WireMock en CI) | Lot 0 ✔ |
| Conditions de réutilisation TAM et Kohesio | À vérifier (réutilisation des données de la Commission) ; à défaut, demande à COMP-TAM-SUPPORT et à la DG REGIO | Avant le lot 5 |
