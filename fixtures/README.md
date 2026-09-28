# Fixtures

Échantillons de données sources pour les tests (sur le classpath des TI de tous les modules).

| Dossier | Origine | Contenu |
|---|---|---|
| `sirene/` | Réponses **réelles** de l'API Sirene 3.11 capturées le 2026-09-27 | Unités légales (société, entreprise cessée, entrepreneur individuel, diffusion partielle), sièges, « aucun résultat », lecture unitaire, pages de parcours par curseur (structure réelle, contenu repris des autres fichiers) |
| `recherche-entreprises/` | **Synthétique**, d'après la documentation OpenAPI de l'API (inaccessible depuis l'environnement cloud) | Résultats de recherche, dont un identifiant invalide |
| `naf/` | Fichiers **réels** de l'INSEE téléchargés le 2026-09-28 (Licence Ouverte), non modifiés : NAF rév. 2 niveau 5 (`naf2008_liste_n5.xls`, 732 sous-classes) et structure de la NAF 2025 (`naf2025_structure.xlsx`, 747 sous-classes) ; empreintes dans `ingestion-sirene/src/main/resources/application.yaml` |
| `decp/` | Lignes **réelles** de l'export `decp-2022-marches-valides` (juin 2026), choisies pour couvrir les pièges (groupement, accord-cadre, modification, sous-traitance, lots d'un même titulaire, plusieurs plateformes, titulaire TVA, montants de 1 € et 1,5 Md€). **Identifiants des titulaires remplacés** par ceux des fixtures Sirene (dont les entités fictives `900000001` et `900000019`) ou par un SIRET de La Poste inconnu des réponses simulées, un SIRET mal saisi (17 caractères) ; acheteurs réels (organismes publics) |
| `ingestion-core/` | **Synthétique** | Deux versions d'un fichier de source fictive (`id;siren;montant;date;nature`) pour les TI du pipeline : montant négatif (rejet), montant modifié, flux qui change d'année, nouvel enregistrement |

**Données personnelles** : l'entrepreneur individuel et l'entité en diffusion partielle portent des SIREN fictifs (`900000001`, `900000019`, clé de Luhn valide), une dénomination fictive (la diffusion partielle d'une personne morale n'en masque pas la dénomination) et des communes fictives. Aucune personne réelle ne figure dans le dépôt. Les titulaires de l'extrait DECP sont remplacés par ces entités fictives ou par des sociétés (personnes morales) déjà présentes dans les fixtures Sirene.
