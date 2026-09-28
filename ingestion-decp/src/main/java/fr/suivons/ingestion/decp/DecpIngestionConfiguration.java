package fr.suivons.ingestion.decp;

import org.jooq.DSLContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import fr.suivons.ingestion.core.IngestionCoreConfiguration;
import fr.suivons.ingestion.core.pipeline.Telechargeur;
import fr.suivons.reconciliation.referentiel.DepotEntreprises;
import fr.suivons.reconciliation.referentiel.ReferentielMinimal;
import fr.suivons.referentiel.ReferentielClientConfiguration;
import fr.suivons.referentiel.sirene.SireneClient;

@Configuration(proxyBeanMethods = false)
@Import({IngestionCoreConfiguration.class, ReferentielClientConfiguration.class})
@EnableConfigurationProperties(DecpProperties.class)
class DecpIngestionConfiguration {

    @Bean
    SourceDecp sourceDecp(DecpProperties reglages, Telechargeur telechargeur) {
        return new SourceDecp(reglages, telechargeur);
    }

    @Bean
    RattacheurSiret rattacheurSiret(SireneClient sirene, DSLContext dsl) {
        return new RattacheurSiret(new ReferentielMinimal(sirene, new DepotEntreprises(dsl)));
    }
}
