package fr.suivons.ingestion.sirene;

import org.jooq.DSLContext;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import fr.suivons.ingestion.core.IngestionCoreConfiguration;
import fr.suivons.ingestion.core.IngestionProperties;
import fr.suivons.ingestion.core.pipeline.DepotRejets;
import fr.suivons.ingestion.core.pipeline.SuiviRuns;
import fr.suivons.ingestion.core.pipeline.Telechargeur;
import fr.suivons.ingestion.sirene.naf.ChargementNaf;
import fr.suivons.ingestion.sirene.naf.DepotNaf;
import fr.suivons.ingestion.sirene.naf.NafProperties;
import fr.suivons.reconciliation.referentiel.DepotEntreprises;
import fr.suivons.reconciliation.referentiel.EntreprisesSansFlux;
import fr.suivons.reconciliation.referentiel.ReferentielMinimal;
import fr.suivons.ingestion.sirene.referentiel.RafraichissementReferentiel;
import fr.suivons.ingestion.sirene.referentiel.ReferentielMinimalProperties;
import fr.suivons.referentiel.ReferentielClientConfiguration;
import fr.suivons.referentiel.sirene.SireneClient;

@Configuration(proxyBeanMethods = false)
@Import({IngestionCoreConfiguration.class, ReferentielClientConfiguration.class})
@EnableConfigurationProperties({NafProperties.class, ReferentielMinimalProperties.class})
class SireneIngestionConfiguration {

    @Bean
    ChargementNaf chargementNaf(NafProperties reglages, Telechargeur telechargeur, SuiviRuns runs, DSLContext dsl,
            PlatformTransactionManager transactions, IngestionProperties ingestion) {
        return new ChargementNaf(reglages, telechargeur, runs, new DepotNaf(dsl), new TransactionTemplate(transactions),
                ingestion.repertoireCache());
    }

    @Bean
    RafraichissementReferentiel rafraichissementReferentiel(JobRepository jobRepository, JobOperator jobOperator,
            PlatformTransactionManager transactions, SuiviRuns runs, DSLContext dsl, SireneClient sirene,
            ReferentielMinimalProperties reglages) {
        DepotEntreprises entreprises = new DepotEntreprises(dsl);
        return new RafraichissementReferentiel(jobRepository, jobOperator, transactions, runs,
                entreprises, new EntreprisesSansFlux(dsl), new DepotRejets(dsl),
                new ReferentielMinimal(sirene, entreprises), reglages);
    }
}
