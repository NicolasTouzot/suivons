package fr.suivons.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class FluxNormaliseTest {

    @Test
    void accepteUnMontantFermeAuxBornesEgales() {
        assertThat(flux(Optional.of(100L), Optional.of(100L), NatureMontant.FERME).annee()).isEqualTo(2024);
    }

    @Test
    void accepteUnPlafondSansBorneFerme() {
        assertThat(flux(Optional.empty(), Optional.of(100L), NatureMontant.PLAFOND).montantFerme()).isEmpty();
    }

    @Test
    void accepteUnMontantInconnuSansBorne() {
        assertThat(flux(Optional.empty(), Optional.empty(), NatureMontant.INCONNU).montantPlafond()).isEmpty();
    }

    @Test
    void refuseUnMontantNegatif() {
        assertThatThrownBy(() -> flux(Optional.of(-1L), Optional.of(100L), NatureMontant.PLAFOND))
                .isInstanceOf(FluxInvalideException.class).hasMessageContaining("négatif");
    }

    @Test
    void refuseUneBorneFermeSuperieureAuPlafond() {
        assertThatThrownBy(() -> flux(Optional.of(200L), Optional.of(100L), NatureMontant.PLAFOND))
                .isInstanceOf(FluxInvalideException.class).hasMessageContaining("supérieure");
    }

    @Test
    void refuseUnMontantFermeAuxBornesDifferentes() {
        assertThatThrownBy(() -> flux(Optional.of(50L), Optional.of(100L), NatureMontant.FERME))
                .isInstanceOf(FluxInvalideException.class).hasMessageContaining("FERME");
    }

    @Test
    void refuseUnPlafondAbsentHorsMontantInconnu() {
        assertThatThrownBy(() -> flux(Optional.empty(), Optional.empty(), NatureMontant.PARTAGE))
                .isInstanceOf(FluxInvalideException.class).hasMessageContaining("plafond");
    }

    @Test
    void exigeLaProvenance() {
        assertThatThrownBy(() -> new FluxNormalise("R1", " ", Canal.MARCHE, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), LocalDate.of(2024, 1, 1), Optional.empty(), Optional.of(1L),
                NatureMontant.PLAFOND, QualiteMontant.OK))
                .isInstanceOf(FluxInvalideException.class).hasMessageContaining("source");
    }

    private static FluxNormalise flux(Optional<Long> ferme, Optional<Long> plafond, NatureMontant nature) {
        return new FluxNormalise("R1", "https://source/R1", Canal.MARCHE, Optional.of(new Siren("552032534")),
                Optional.of("DANONE"), Optional.of(new Payeur("13000501000033", Optional.of("Acheteur"), TypePayeur.ETAT)),
                Optional.of("Objet"), LocalDate.of(2024, 5, 2), ferme, plafond, nature, QualiteMontant.OK);
    }
}
