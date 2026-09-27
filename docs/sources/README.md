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

## Écarts à la spec : décisions du 2026-09-27

Toutes reportées dans `SPEC.md` v0.3.

| # | Constat | Décision | Où |
|---|---|---|---|
| 1 | DECP : data.gouv.fr inaccessible depuis le cloud ; la DAJ publie les DECP consolidées sur `data.economie.gouv.fr` en deux formats | Source DECP = jeux DAJ `decp-v3-marches-valides` (2018-2023) + `decp-2022-marches-valides` (2024 →), export Parquet ; équivalence avec data.gouv.fr à vérifier en local, sans bloquer | SPEC §4 |
| 2 | DECP : lignes aplaties, montants répétés, sentinelle `CDL`, pas de clé naturelle | Un flux par (marché, titulaire), `source_record_id = acheteur\|id\|titulaire`, dernier montant connu, sous-traitance sans montant, fusion des doublons de plateformes | SPEC §6.4 |
| 3 | TAM : pas d'API, export plafonné à ~1 000 lignes par recherche | Collecte par fenêtres de dates adaptatives ; conditions de réutilisation à vérifier avant le lot 5 | SPEC §4, §14 |
| 4 | TAM : deux montants, tranches pour l'aide fiscale | Référence = élément d'aide ; nominal affiché en complément (F3), jamais agrégé ; tranche → ferme = bas, plafond = haut | SPEC §6.4 |
| 5 | Kohesio : montant par projet, plusieurs bénéficiaires possibles | Bénéficiaire unique → `FERME` ; plusieurs → `PARTAGE` (ferme nul, plafond = montant du projet) ; montant qualifié de « programmé » ; collecte incrémentale | SPEC §4, §6.4 |
| 6 | ~5 % des titulaires DECP sont des entrepreneurs individuels | Masquage si `statutDiffusion = P` **ou** catégorie juridique `1000` | SPEC §4, §6.3, §11.2 |
| 7 | Recherche d'entreprises bloquée depuis le cloud | API conservée : elle fonctionne ailleurs, le blocage vient de la détection des robots par le fournisseur sur cet environnement. Tests en session locale, WireMock en CI ; accès vérifié depuis l'hébergeur retenu (critère du lot 7) ; mode dégradé | SPEC §4, §14 |
| 8 | Registre de minimis : SIREN direct, petites aides depuis 2026 | **V2** ; licence à vérifier | SPEC §2 |

## Reste à faire en session locale

- Recherche d'entreprises : temps de réponse, quotas réels, qualité du score sur des noms Kohesio et TAM.
- data.gouv.fr : comparer le jeu DECP consolidé avec ceux de la DAJ.
- Conditions de réutilisation TAM et Kohesio (et `linkedopendata.eu`).
