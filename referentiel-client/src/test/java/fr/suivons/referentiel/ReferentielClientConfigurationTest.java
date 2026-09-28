package fr.suivons.referentiel;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import fr.suivons.referentiel.recherche.RechercheEntreprisesClient;
import fr.suivons.referentiel.recherche.RechercheEntreprisesProperties;
import fr.suivons.referentiel.sirene.SireneClient;
import fr.suivons.referentiel.sirene.SireneProperties;

class ReferentielClientConfigurationTest {

    private final ApplicationContextRunner contexte =
            new ApplicationContextRunner().withUserConfiguration(ReferentielClientConfiguration.class);

    @Test
    void fournitLesClientsAvecDesValeursParDefautConformesAuxQuotasDesApi() {
        contexte.run(c -> {
            assertThat(c).hasSingleBean(SireneClient.class).hasSingleBean(RechercheEntreprisesClient.class);
            SireneProperties sirene = c.getBean(SireneProperties.class);
            assertThat(sirene.url()).isEqualTo(URI.create("https://api.insee.fr/api-sirene/3.11"));
            assertThat(sirene.cleApi()).isEmpty();
            assertThat(sirene.requetesParMinute()).isEqualTo(30);
            assertThat(sirene.tailleLot()).isEqualTo(1000);
            assertThat(sirene.dureeCache()).isEqualTo(Duration.ofHours(24));
            assertThat(sirene.protection().tentatives()).isEqualTo(3);
            RechercheEntreprisesProperties recherche = c.getBean(RechercheEntreprisesProperties.class);
            assertThat(recherche.requetesParSeconde()).isLessThanOrEqualTo(7);
        });
    }

    @Test
    void litLaCleEtLesReglagesDepuisLaConfiguration() {
        contexte.withPropertyValues(
                        "suivons.referentiel.sirene.cle-api=ma-cle",
                        "suivons.referentiel.sirene.delai-maximal=3s",
                        "suivons.referentiel.sirene.protection.tentatives=5")
                .run(c -> {
                    SireneProperties sirene = c.getBean(SireneProperties.class);
                    assertThat(sirene.cleApi()).isEqualTo("ma-cle");
                    assertThat(sirene.delaiMaximal()).isEqualTo(Duration.ofSeconds(3));
                    assertThat(sirene.protection().tentatives()).isEqualTo(5);
                });
    }
}
