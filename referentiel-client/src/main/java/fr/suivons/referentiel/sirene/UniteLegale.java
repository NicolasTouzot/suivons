package fr.suivons.referentiel.sirene;

import java.util.Optional;

import fr.suivons.domain.Siren;

/**
 * Unité légale telle que lue dans l'API Sirene (période courante). Les valeurs masquées par l'INSEE
 * (« [ND] », diffusion partielle) sont absentes.
 */
public record UniteLegale(
        Siren siren,
        boolean diffusible,
        Optional<String> denomination,
        Optional<String> categorieJuridique,
        Optional<ActivitePrincipale> activitePrincipale,
        boolean active,
        Optional<String> categorieEntreprise) {

    /** Catégorie juridique réservée aux entrepreneurs individuels : jamais d'exposition nominative (SPEC §11.2). */
    public static final String CATEGORIE_ENTREPRENEUR_INDIVIDUEL = "1000";

    public boolean personnePhysique() {
        return categorieJuridique.filter(CATEGORIE_ENTREPRENEUR_INDIVIDUEL::equals).isPresent();
    }
}
