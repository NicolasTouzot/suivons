package fr.suivons.referentiel.recherche;

import java.util.Optional;

import fr.suivons.domain.Siren;

/** Entreprise candidate au rattachement par nom (SPEC §6.5) ou au repli de recherche (F1). */
public record CandidatEntreprise(
        Siren siren,
        String nom,
        boolean active,
        Optional<String> natureJuridique,
        Optional<String> codeCommuneSiege,
        Optional<String> communeSiege,
        Optional<String> codePostalSiege,
        Optional<Double> score) {
}
