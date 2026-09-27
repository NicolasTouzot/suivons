package fr.suivons.referentiel;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import fr.suivons.referentiel.recherche.RechercheEntreprisesClient;
import fr.suivons.referentiel.recherche.RechercheEntreprisesProperties;
import fr.suivons.referentiel.sirene.SireneClient;
import fr.suivons.referentiel.sirene.SireneProperties;

/**
 * Clients des API d'appui, à importer explicitement par les applications qui en ont besoin
 * (`@Import(ReferentielClientConfiguration.class)`). La clé Sirene se configure par
 * `suivons.referentiel.sirene.cle-api=${INSEE_API_KEY:}`.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SireneProperties.class, RechercheEntreprisesProperties.class})
public class ReferentielClientConfiguration {

    @Bean
    SireneClient sireneClient(SireneProperties reglages) {
        return new SireneClient(reglages);
    }

    @Bean
    RechercheEntreprisesClient rechercheEntreprisesClient(RechercheEntreprisesProperties reglages) {
        return new RechercheEntreprisesClient(reglages);
    }
}
