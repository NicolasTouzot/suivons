package fr.suivons.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Organisme qui paie : acheteur public, autorité d'octroi, programme européen.
 *
 * @param identifiant SIRET ou SIREN de l'acheteur, ou identifiant stable propre à la source
 */
public record Payeur(String identifiant, Optional<String> nom, TypePayeur type) {

    public Payeur {
        if (identifiant == null || identifiant.isBlank()) {
            throw new IllegalArgumentException("Identifiant de payeur absent");
        }
        Objects.requireNonNull(nom, "nom");
        Objects.requireNonNull(type, "type");
    }
}
