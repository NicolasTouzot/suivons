# Fixtures

Échantillons de données sources pour les tests (sur le classpath des TI de tous les modules).

| Dossier | Origine | Contenu |
|---|---|---|
| `sirene/` | Réponses **réelles** de l'API Sirene 3.11 capturées le 2026-09-27 | Unités légales (société, entreprise cessée, entrepreneur individuel, diffusion partielle), sièges, « aucun résultat », lecture unitaire, pages de parcours par curseur (structure réelle, contenu repris des autres fichiers) |
| `recherche-entreprises/` | **Synthétique**, d'après la documentation OpenAPI de l'API (inaccessible depuis l'environnement cloud) | Résultats de recherche, dont un identifiant invalide |

**Données personnelles** : l'entrepreneur individuel et l'entité en diffusion partielle portent des SIREN fictifs (`900000001`, `900000019`, clé de Luhn valide), une dénomination fictive (la diffusion partielle d'une personne morale n'en masque pas la dénomination) et des communes fictives. Aucune personne réelle ne figure dans le dépôt.
| `naf/` | Fichiers **réels** de l'INSEE téléchargés le 2026-09-28 (Licence Ouverte), non modifiés : NAF rév. 2 niveau 5 (`naf2008_liste_n5.xls`, 732 sous-classes) et structure de la NAF 2025 (`naf2025_structure.xlsx`, 747 sous-classes) ; empreintes dans `ingestion-sirene/src/main/resources/application.yaml` |
| `ingestion-core/` | **Synthétique** | Deux versions d'un fichier de source fictive (`id;siren;montant;date;nature`) pour les TI du pipeline : montant négatif (rejet), montant modifié, flux qui change d'année, nouvel enregistrement |
