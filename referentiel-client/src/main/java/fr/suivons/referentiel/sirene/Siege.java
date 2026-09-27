package fr.suivons.referentiel.sirene;

import java.util.Optional;

import fr.suivons.domain.Departement;
import fr.suivons.domain.Siren;

/** Établissement siège d'une unité légale : localisation minimale du référentiel (SPEC §6.3). */
public record Siege(Siren siren, String siret, Optional<String> codeCommune, Optional<String> libelleCommune) {

    public Optional<String> departement() {
        return codeCommune.flatMap(Departement::depuisCodeCommune);
    }
}
