package fr.suivons.ingestion.core;

import org.jooq.DSLContext;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import fr.suivons.ingestion.core.pipeline.DepotBrut;
import fr.suivons.ingestion.core.pipeline.DepotFlux;
import fr.suivons.ingestion.core.pipeline.DepotRejets;
import fr.suivons.ingestion.core.pipeline.PipelineIngestion;
import fr.suivons.ingestion.core.pipeline.SuiviRuns;
import fr.suivons.ingestion.core.pipeline.Telechargeur;

/**
 * Pipeline d'ingestion, à importer par chaque application `ingestion-<source>`
 * (`@Import(IngestionCoreConfiguration.class)`). Les métadonnées Spring Batch vont dans ops (préfixe `batch_`).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IngestionProperties.class)
public class IngestionCoreConfiguration {

    @Bean
    SuiviRuns suiviRuns(DSLContext dsl) {
        return new SuiviRuns(dsl);
    }

    @Bean
    Telechargeur telechargeur(IngestionProperties reglages) {
        return new Telechargeur(reglages.delaiTelechargement(), reglages.userAgent());
    }

    @Bean
    PipelineIngestion pipelineIngestion(JobRepository jobRepository, JobOperator jobOperator,
            PlatformTransactionManager transactions, DSLContext dsl, SuiviRuns runs, IngestionProperties reglages) {
        return new PipelineIngestion(jobRepository, jobOperator, transactions, runs, new DepotBrut(dsl),
                new DepotFlux(dsl), new DepotRejets(dsl), reglages.repertoireCache(), reglages.tailleLot());
    }
}
