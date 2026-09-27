package fr.suivons.referentiel.recherche;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Formes JSON de l'API Recherche d'entreprises (`GET /search`), limitées aux champs utiles. */
final class ReponsesRecherche {

    private ReponsesRecherche() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resultats(List<Resultat> results, @JsonProperty("total_results") Integer totalResults) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resultat(
            String siren,
            @JsonProperty("nom_complet") String nomComplet,
            @JsonProperty("etat_administratif") String etatAdministratif,
            @JsonProperty("nature_juridique") String natureJuridique,
            SiegeJson siege,
            Double score) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SiegeJson(
            String commune,
            @JsonProperty("libelle_commune") String libelleCommune,
            @JsonProperty("code_postal") String codePostal) {
    }
}
