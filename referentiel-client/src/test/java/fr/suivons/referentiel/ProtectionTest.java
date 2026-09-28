package fr.suivons.referentiel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class ProtectionTest {

    private static final ProtectionProperties REGLAGES = new ProtectionProperties(3, Duration.ofSeconds(1),
            Duration.ofSeconds(60), 50, 5, Duration.ofSeconds(60), Duration.ofSeconds(70));

    @Test
    void lisseLeDebitAuLieuDAutoriserUneRafale() {
        assertThat(new Protection("sirene", 30, Duration.ofMinutes(1), REGLAGES).intervalleEntreAppels())
                .as("30 appels par minute : un appel toutes les 2 s").isEqualTo(Duration.ofSeconds(2));
        assertThat(new Protection("recherche", 7, Duration.ofSeconds(1), REGLAGES).intervalleEntreAppels())
                .isEqualTo(Duration.ofNanos(142_857_142));
    }
}
