# Sources de données

> Résultats du spike du lot 0 (étape 9, 2026-09-27) : accès réels, formats, volumétrie, identifiants, pièges. Les notes décrivent ce qui a été **constaté** ; la spec (`SPEC.md` §4) reste la référence tant que les écarts ci-dessous ne sont pas tranchés.

| Source | Canal | Note | Accès depuis le cloud | Identifiant bénéficiaire | Volume (France) |
|---|---|---|---|---|---|
| DECP (DAJ) | `MARCHE` | [decp.md](decp.md) | ✅ `data.economie.gouv.fr` (data.gouv.fr ❌) | SIRET 99,8 % | ~1,4 M lignes (2018 → aujourd'hui, 2 formats) |
| TAM | `AIDE_ETAT` | [tam.md](tam.md) | ✅ formulaire web + export CSV | SIREN/SIRET ~100 % (formats hétérogènes) | dizaines de milliers d'aides par an |
| Kohesio | `FONDS_UE` | [kohesio.md](kohesio.md) | ✅ API JSON non documentée (linkedopendata ❌) | **aucun** (nom seul) | 63 k projets, 19,6 k bénéficiaires |
| API Sirene 3.11 | appui | [sirene.md](sirene.md) | ✅ | — | — |
| API Recherche d'entreprises | appui | [recherche-entreprises.md](recherche-entreprises.md) | ❌ filtrage du fournisseur | — | — |
| Pistes hors spec | — | [pistes.md](pistes.md) | ✅ | — | — |

## Écarts à la spec et décisions à prendre

| # | Constat | Proposition | Impact |
|---|---|---|---|
| 1 | DECP : la source prévue (data.gouv.fr, format tabulaire) est inaccessible depuis le cloud ; la DAJ publie les mêmes données consolidées sur `data.economie.gouv.fr`, en deux formats (2019 pour 2018-2023, 2022 pour 2024 →) | Source DECP = jeux DAJ `decp-v3-marches-valides` + `decp-2022-marches-valides`, export Parquet ; vérifier en session locale l'équivalence avec le jeu data.gouv.fr | SPEC §4 |
| 2 | DECP : lignes aplaties (modifications, sous-traitance), montants répétés, sentinelle `CDL`, pas de clé naturelle | Règles de dédoublonnage et `source_record_id` composé, à écrire dans la méthodologie avant le lot 3 | SPEC §4 (pièges), §6.4 |
| 3 | TAM : aucune API ; export CSV plafonné à ~1 000 lignes par recherche ; conditions de réutilisation non trouvées | Collecte par fenêtres de dates adaptatives ; vérifier les conditions de réutilisation (ou demander un accès à COMP-TAM-SUPPORT) | SPEC §4, risque lot 4 |
| 4 | TAM : deux montants (nominal / élément d'aide), montants en tranches pour l'aide fiscale | Choisir le montant de référence (proposition : élément d'aide ; nominal affiché en complément) ; tranche → ferme = bas, plafond = haut | SPEC §6.4, méthodologie |
| 5 | Kohesio : API non documentée, montant par projet et non par bénéficiaire, collecte lente | Collecte incrémentale ; règle de répartition multi-bénéficiaires (proposition : plafond = montant du projet, ferme = 0) ; montant programmé et non versé | SPEC §4, §6.4 |
| 6 | Sirene : ~5 % des titulaires DECP sont des entrepreneurs individuels (personnes physiques) | Masquage si `statutDiffusion = P` **ou** catégorie juridique `1000` | SPEC §6.3, §11.2 |
| 7 | Recherche d'entreprises : bloquée par ASN depuis le cloud ; limite 30 req/s par ASN | Spike en local ; critère d'hébergement ; mode dégradé | SPEC §4, §14 |
| 8 | Registre de minimis : SIREN direct, petites aides d'État depuis 2026 | Candidat au canal `AIDE_ETAT` (MVP ou V2), licence à vérifier | SPEC §2, §4 |

## Reste à faire en session locale

- Recherche d'entreprises : temps de réponse, quotas réels, qualité du score sur des noms Kohesio et TAM.
- data.gouv.fr : comparer le jeu DECP consolidé avec ceux de la DAJ.
- Conditions de réutilisation TAM et Kohesio (et `linkedopendata.eu`).
