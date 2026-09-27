package fr.suivons.referentiel.sirene;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Formes JSON des réponses de l'API Sirene 3.11, limitées aux champs demandés. */
final class ReponsesSirene {

    private ReponsesSirene() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entete(Integer total, Integer nombre, String curseur, String curseurSuivant) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UnitesLegales(Entete header, List<UniteLegaleJson> unitesLegales) {

        static final UnitesLegales AUCUNE = new UnitesLegales(null, List.of());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UneUniteLegale(Entete header, UniteLegaleJson uniteLegale) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UniteLegaleJson(
            String siren,
            String statutDiffusionUniteLegale,
            String categorieEntreprise,
            List<PeriodeJson> periodesUniteLegale) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PeriodeJson(
            String dateFin,
            String etatAdministratifUniteLegale,
            String denominationUniteLegale,
            String categorieJuridiqueUniteLegale,
            String activitePrincipaleUniteLegale,
            String nomenclatureActivitePrincipaleUniteLegale) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Etablissements(Entete header, List<EtablissementJson> etablissements) {

        static final Etablissements AUCUN = new Etablissements(null, List.of());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EtablissementJson(String siren, String siret, AdresseJson adresseEtablissement) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AdresseJson(String codeCommuneEtablissement, String libelleCommuneEtablissement) {
    }
}
