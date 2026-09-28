package fr.suivons.ingestion.core.source;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import fr.suivons.domain.Siren;
import fr.suivons.domain.StatutRattachement;

/** Résultat du rattachement : un flux non résolu n'a jamais de SIREN (SPEC.md §6.5). */
public record Rattachement(StatutRattachement statut, Optional<Siren> siren, Optional<BigDecimal> confiance) {

    public Rattachement {
        Objects.requireNonNull(statut, "statut");
        Objects.requireNonNull(siren, "siren");
        Objects.requireNonNull(confiance, "confiance");
        if ((statut == StatutRattachement.NON_RESOLU) == siren.isPresent()) {
            throw new IllegalArgumentException("Rattachement incohérent : " + statut + " / " + siren);
        }
    }

    public static Rattachement nonResolu() {
        return new Rattachement(StatutRattachement.NON_RESOLU, Optional.empty(), Optional.empty());
    }

    public static Rattachement sirenSource(Siren siren) {
        return new Rattachement(StatutRattachement.SIREN_SOURCE, Optional.of(siren), Optional.of(BigDecimal.ONE));
    }
}
