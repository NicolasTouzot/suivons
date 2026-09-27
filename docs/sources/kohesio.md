# Kohesio — projets de la politique de cohésion de l'UE (canal `FONDS_UE`)

> Spike du 2026-09-27 (lot 0, étape 9).

## Accès

| | |
|---|---|
| Producteur | Commission européenne, DG REGIO ; données des autorités de gestion |
| Interface | `https://kohesio.ec.europa.eu` (application Angular) |
| API | **JSON non documentée**, utilisée par l'interface, sans clé : base `https://kohesio.ec.europa.eu/api` |
| Données liées | `linkedopendata.eu` (Wikibase) et son point SPARQL : **refusés depuis l'environnement cloud** (403 du pare-feu AWS) |
| Licence | non vérifiée : **à confirmer** (réutilisation des données de la Commission, en principe CC BY 4.0) |

Pays France : `country=https://linkedopendata.eu/entity/Q20`.

| Endpoint | Usage | Constat |
|---|---|---|
| `GET /api/projects?language=fr&country=…&limit=&offset=` | liste des projets | 63 304 projets pour la France ; pagination OK (offset 9 950 testé) ; champs `item`, `labels`, `euBudgets`, `totalBudgets`, `startTimes`, `endTimes`… **sans bénéficiaire** |
| `GET /api/projects/{Q}?id=https://linkedopendata.eu/entity/{Q}&language=fr` | détail d'un projet | `budget`, `euBudget`, `cofinancingRate`, `startTime`, `endTime`, `program`, `funds`, `region`, `managingAuthorityLabel`, **`beneficiaries[]`** (`beneficiaryLabel`, `link`, `website`, `wikidata`) ; ~1,3 s par appel |
| `GET /api/beneficiaries?language=fr&country=…&limit=&offset=` | bénéficiaires agrégés | 19 585 bénéficiaires pour la France ; `label`, `budget`, `euBudget`, `numberProjects` ; **offset limité** (400 au-delà d'environ 1 000) |
| `GET /api/projects/download/csv?…` | export CSV | limité à 1 000 lignes, sans bénéficiaire, encodage cassé : **inutilisable** |

## Pièges constatés

1. **Aucun identifiant national** du bénéficiaire (ni SIREN, ni SIRET) : rattachement par le nom uniquement, comme prévu (SPEC §4). Les bénéficiaires sont des collectivités, des établissements publics, des associations et des entreprises ; le libellé est en majuscules ou en casse mixte, parfois avec la forme juridique.
2. **Montant par projet, pas par bénéficiaire** : `euBudget` porte sur le projet. Un projet à plusieurs bénéficiaires n'indique pas la part de chacun. Sur l'échantillon (20 projets), un seul bénéficiaire à chaque fois, mais la règle de répartition doit être fixée (proposition : ferme = 0 et plafond = montant du projet pour chaque co-bénéficiaire, ou pas de rattachement).
3. **Montant programmé, pas versé** : `euBudget` est le soutien UE prévu (période 2014-2020 et 2021-2027), à qualifier dans la méthodologie.
4. **Collecte lente** : le bénéficiaire n'est disponible que dans le détail du projet, soit ~23 h pour 63 000 appels en séquentiel. Collecte **incrémentale** (nouveaux projets et projets modifiés) et parallélisme modéré.
5. **API non documentée** : aucune garantie de stabilité. Tests sur des réponses enregistrées et alerte en cas de changement de schéma.
