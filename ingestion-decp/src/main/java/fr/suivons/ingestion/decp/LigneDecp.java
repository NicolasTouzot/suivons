package fr.suivons.ingestion.decp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ligne du jeu `decp-2022-marches-valides` (export JSON de data.economie.gouv.fr), limitée aux colonnes utiles.
 * Une ligne est une combinaison marché × co-titulaires × modification × acte de sous-traitance
 * (docs/sources/decp.md).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LigneDecp(
        @JsonProperty("acheteur_id") String acheteurId,
        @JsonProperty("id") String id,
        @JsonProperty("objet") String objet,
        @JsonProperty("techniques") String techniques,
        @JsonProperty("montant") BigDecimal montant,
        @JsonProperty("datenotification") String dateNotification,
        @JsonProperty("datepublicationdonnees") String datePublication,
        @JsonProperty("source") String plateforme,
        @JsonProperty("titulaire_id_1") String titulaireId1,
        @JsonProperty("titulaire_typeidentifiant_1") String titulaireType1,
        @JsonProperty("titulaire_id_2") String titulaireId2,
        @JsonProperty("titulaire_typeidentifiant_2") String titulaireType2,
        @JsonProperty("titulaire_id_3") String titulaireId3,
        @JsonProperty("titulaire_typeidentifiant_3") String titulaireType3,
        @JsonProperty("idmodification") String idModification,
        @JsonProperty("montantmodification") String montantModification,
        @JsonProperty("datenotificationmodificationmodification") String dateModification) {

    /** Valeur sentinelle du producteur pour « vide ». */
    static final String VIDE = "CDL";

    /** Titulaire d'une ligne : identifiant et type d'identifiant (SIRET, TVA, HORS-UE…). */
    public record Titulaire(String identifiant, String type) {
    }

    /** Titulaires renseignés de la ligne ; plus d'un : groupement de co-titulaires. */
    public List<Titulaire> titulaires() {
        List<Titulaire> titulaires = new ArrayList<>(3);
        ajouter(titulaires, titulaireId1, titulaireType1);
        ajouter(titulaires, titulaireId2, titulaireType2);
        ajouter(titulaires, titulaireId3, titulaireType3);
        return titulaires;
    }

    /** Montant apporté par la modification de la ligne, s'il y en a un. */
    public Optional<BigDecimal> montantModifie() {
        return valeur(montantModification).map(BigDecimal::new);
    }

    public Optional<String> modification() {
        return valeur(idModification);
    }

    public Optional<String> dateModificationOuVide() {
        return valeur(dateModification);
    }

    public boolean accordCadre() {
        return techniques != null && techniques.contains("Accord-cadre");
    }

    static Optional<String> valeur(String brute) {
        if (brute == null || brute.isBlank() || VIDE.equals(brute)) {
            return Optional.empty();
        }
        return Optional.of(brute.strip());
    }

    private static void ajouter(List<Titulaire> titulaires, String identifiant, String type) {
        valeur(identifiant).ifPresent(id -> titulaires.add(new Titulaire(id, valeur(type).orElse(""))));
    }
}
