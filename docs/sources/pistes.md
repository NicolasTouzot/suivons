# Pistes hors spec (sources candidates et données de contexte)

> Repérées les 26 et 27/09/2026. Aucune n'est dans le périmètre du MVP : l'ajout d'une source passe par une mise à jour de `SPEC.md` §4 et de la méthodologie.

## Sources de flux candidates

| Source | Accès | Contenu | Intérêt | Points à vérifier |
|---|---|---|---|---|
| **Registre public des aides de minimis** (DGE) | `data.economie.gouv.fr`, jeu `aides_minimis` (Opendatasoft, sans clé) | par aide : `nom_beneficiaire`, `identifiant_beneficiaire` (SIREN), `montant_esb`, `instrument_aide`, `autorite`, `date_octroi` ; 17 621 lignes, octrois depuis le 01/01/2026 (agricole : 2027) ; mise à jour quotidienne | **fort** : couvre les petites aides d'État sous le seuil TAM, rattachement direct par SIREN (canal `AIDE_ETAT`) | licence non renseignée ; historique court ; recouvrement éventuel avec TAM |
| **BOAMP** (DILA) | `boamp-datadila.opendatasoft.com`, jeu `boamp` (1,7 M avis, quotidien) | 466 k avis « Résultat de marché » depuis 2015 ; JSON eForms `donnees` avec SIRET (`cbc:CompanyID`) et montants attribués (`cbc:PayableAmount`, `cbc:TotalAmount`) ; `url_avis` | **moyen** : contrôle croisé et complétude de DECP (canal `MARCHE`) | licence non renseignée ; doublons avec DECP ; extraction du JSON eForms |
| Plan de relance, projets industriels (DGE) | `data.economie.gouv.fr`, jeu `plan-de-relance` | 3 080 projets avec SIREN, **sans montant par projet** | faible | — |
| France Num (DGE) | `data.economie.gouv.fr` | 284 k bénéficiaires **pseudonymisés** | nul (pas de rattachement possible) | — |

## Données de contexte et d'appui

| Source | Accès | Usage possible |
|---|---|---|
| **Annuaire de l'administration** (DILA) | `api-lannuaire.service-public.gouv.fr`, jeu `api-lannuaire-administration` (94 k entités, dont 44 k avec SIREN) | qualifier et regrouper les **payeurs** (type d'organisme, hiérarchie) |
| **INSEE Melodi** | `https://api.insee.fr/melodi` (sans clé ; ne pas envoyer la clé Sirene) | euros constants (`DS_COEFF_EURO_FRANC`, `DS_IPC_PRINC`), ordres de grandeur nationaux pour la couverture (`DS_CNA_APU`), dénominateurs sectoriels (`DS_SIDE_STOCKS_A21`, `DS_FLORES_*`, V2) |
| info-financiere.gouv.fr (AMF) | Opendatasoft, jeu `flux-amf-new-prod` | documents réglementés des sociétés cotées (LEI, ISIN, sans SIREN) : lien vers les rapports annuels, V2 au plus |
