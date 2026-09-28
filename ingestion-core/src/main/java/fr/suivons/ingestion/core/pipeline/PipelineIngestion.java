package fr.suivons.ingestion.core.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.transaction.PlatformTransactionManager;

import fr.suivons.domain.FluxInvalideException;
import fr.suivons.ingestion.core.source.ContexteExtraction;
import fr.suivons.ingestion.core.source.EnregistrementSource;
import fr.suivons.ingestion.core.source.RafraichissementMart;
import fr.suivons.ingestion.core.source.Rattachement;
import fr.suivons.ingestion.core.source.Rattacheur;
import fr.suivons.ingestion.core.source.SourceFlux;
import fr.suivons.ingestion.core.source.Transformation;

/**
 * Pipeline commun d'ingestion (SPEC.md §7.3) : extract → load_raw → transform → reconcile → upsert → refresh →
 * report, sous forme de job Spring Batch. Chaque lancement crée un run dans ops.ingestion_run ; si le fichier est
 * identique à celui du dernier run réussi, le run s'arrête sans rien changer.
 */
public class PipelineIngestion {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineIngestion.class);
    static final String RIEN_A_FAIRE = "RIEN_A_FAIRE";

    private final JobRepository jobRepository;
    private final JobOperator jobOperator;
    private final PlatformTransactionManager transactions;
    private final SuiviRuns runs;
    private final DepotBrut brut;
    private final DepotFlux depotFlux;
    private final DepotRejets rejets;
    private final Path repertoireCache;
    private final int tailleLot;

    public PipelineIngestion(JobRepository jobRepository, JobOperator jobOperator,
            PlatformTransactionManager transactions, SuiviRuns runs, DepotBrut brut, DepotFlux depotFlux,
            DepotRejets rejets, Path repertoireCache, int tailleLot) {
        this.jobRepository = jobRepository;
        this.jobOperator = jobOperator;
        this.transactions = transactions;
        this.runs = runs;
        this.brut = brut;
        this.depotFlux = depotFlux;
        this.rejets = rejets;
        this.repertoireCache = repertoireCache;
        this.tailleLot = tailleLot;
    }

    /** Options d'un lancement. {@code toutRetraiter} retransforme toutes les données brutes (règles modifiées). */
    public record Options(boolean toutRetraiter) {

        public static final Options PAR_DEFAUT = new Options(false);
    }

    /** Résultat d'un lancement, tel qu'enregistré dans ops.ingestion_run. */
    public record Resultat(long runId, SuiviRuns.Statut statut, boolean rienAFaire, SuiviRuns.Compteurs compteurs) {
    }

    public Resultat executer(SourceFlux source, Rattacheur rattacheur, RafraichissementMart mart, Options options) {
        EtatRun etat = new EtatRun();
        Job job = job(source, rattacheur, mart, options, etat);
        try {
            JobExecution execution = jobOperator.start(job, new JobParametersBuilder()
                    .addString("source", source.code())
                    .addLong("lancement", System.nanoTime())
                    .addString("toutRetraiter", String.valueOf(options.toutRetraiter()))
                    .toJobParameters());
            SuiviRuns.Statut statut = execution.getStatus() == BatchStatus.COMPLETED
                    ? SuiviRuns.Statut.SUCCES
                    : SuiviRuns.Statut.ECHEC;
            return new Resultat(etat.runId, statut, etat.rienAFaire, etat.compteurs());
        } catch (Exception e) {
            throw new IngestionException("Lancement de l'ingestion " + source.code() + " impossible", e);
        }
    }

    private Job job(SourceFlux source, Rattacheur rattacheur, RafraichissementMart mart, Options options,
            EtatRun etat) {
        Step extraction = new StepBuilder("extraction-" + source.code(), jobRepository)
                .tasklet((contribution, contexte) -> {
                    etat.fichier = source.extraire(new ContexteExtraction(etat.runId,
                            repertoireCache.resolve(source.code().toLowerCase())));
                    String checksum = Checksums.sha256(etat.fichier.chemin());
                    runs.noterFichier(etat.runId, checksum, etat.fichier.versionSource());
                    if (!options.toutRetraiter()
                            && runs.dernierChecksumReussi(source.code()).filter(checksum::equals).isPresent()) {
                        etat.rienAFaire = true;
                        contribution.setExitStatus(new ExitStatus(RIEN_A_FAIRE));
                    }
                    return RepeatStatus.FINISHED;
                }, transactions)
                .build();

        Step chargementBrut = new StepBuilder("chargement-brut-" + source.code(), jobRepository)
                .<EnregistrementSource, EnregistrementSource>chunk(tailleLot)
                .transactionManager(transactions)
                .reader(lecteurFichier(source, etat))
                .writer(lot -> brut.enregistrer(source.tableBrute(), etat.runId, lot.getItems()))
                .build();

        Step transformation = new StepBuilder("transformation-" + source.code(), jobRepository)
                .<EnregistrementSource, Traitement>chunk(tailleLot)
                .transactionManager(transactions)
                .reader(lecteurBrut(source, etat, options))
                .processor(enregistrement -> traiter(source, rattacheur, enregistrement))
                .writer(lot -> ecrire(source, etat, lot))
                .build();

        Step finalisation = new StepBuilder("finalisation-" + source.code(), jobRepository)
                .tasklet((contribution, contexte) -> {
                    if (!etat.rienAFaire) {
                        mart.rafraichir();
                    }
                    return RepeatStatus.FINISHED;
                }, transactions)
                .build();

        return new JobBuilder("ingestion-" + source.code(), jobRepository)
                .listener(new JobExecutionListener() {
                    @Override
                    public void beforeJob(JobExecution execution) {
                        etat.runId = runs.demarrer(source.code());
                        LOG.info("Ingestion {} : run {} démarré", source.code(), etat.runId);
                    }

                    @Override
                    public void afterJob(JobExecution execution) {
                        SuiviRuns.Statut statut = execution.getStatus() == BatchStatus.COMPLETED
                                ? SuiviRuns.Statut.SUCCES
                                : SuiviRuns.Statut.ECHEC;
                        runs.terminer(etat.runId, statut, etat.compteurs());
                        LOG.info("Ingestion {} : run {} terminé, {} ({} lus, {} chargés, {} rejetés{})", source.code(),
                                etat.runId, statut, etat.lus.get(), etat.charges.get(), etat.rejetes.get(),
                                etat.rienAFaire ? ", fichier inchangé" : "");
                    }
                })
                .start(extraction)
                .on(RIEN_A_FAIRE).to(finalisation)
                .from(extraction).on("*").to(chargementBrut).next(transformation).next(finalisation)
                .end()
                .build();
    }

    private Traitement traiter(SourceFlux source, Rattacheur rattacheur, EnregistrementSource enregistrement) {
        Transformation transformation;
        try {
            transformation = source.transformer(enregistrement);
        } catch (FluxInvalideException e) {
            transformation = Transformation.rejet(enregistrement.sourceRecordId(), e.getMessage());
        }
        return switch (transformation) {
            case Transformation.Rejet rejet -> Traitement.rejet(new DepotRejets.RejetMotive(
                    enregistrement.sourceRecordId(), rejet.motif(), enregistrement.payload()));
            case Transformation.Flux flux -> {
                Rattachement rattachement = rattacheur.rattacher(flux.flux());
                yield Traitement.flux(new DepotFlux.FluxRattache(flux.flux(), rattachement));
            }
        };
    }

    private void ecrire(SourceFlux source, EtatRun etat, Chunk<? extends Traitement> lot) {
        List<DepotFlux.FluxRattache> flux = new ArrayList<>();
        List<DepotRejets.RejetMotive> motives = new ArrayList<>();
        for (Traitement traitement : lot) {
            traitement.flux().ifPresent(flux::add);
            traitement.rejet().ifPresent(motives::add);
        }
        etat.charges.addAndGet(depotFlux.ecrire(source.code(), etat.runId, etat.extraitLe, flux));
        etat.rejetes.addAndGet(rejets.enregistrer(etat.runId, motives));
    }

    /** Lit le fichier de la source ; compte les enregistrements lus. */
    private ItemStreamReader<EnregistrementSource> lecteurFichier(SourceFlux source, EtatRun etat) {
        return new ItemStreamReader<>() {
            private Stream<EnregistrementSource> flux;
            private Iterator<EnregistrementSource> iterateur;

            @Override
            public void open(ExecutionContext contexte) {
                try {
                    flux = source.lire(etat.fichier);
                    iterateur = flux.iterator();
                } catch (IOException e) {
                    throw new UncheckedIOException("Lecture de " + etat.fichier.chemin() + " impossible", e);
                }
            }

            @Override
            public EnregistrementSource read() {
                if (iterateur == null || !iterateur.hasNext()) {
                    return null;
                }
                etat.lus.incrementAndGet();
                return iterateur.next();
            }

            @Override
            public void close() {
                if (flux != null) {
                    flux.close();
                }
            }
        };
    }

    /** Parcourt les données brutes à transformer par pages, dans l'ordre des identifiants. */
    private ItemStreamReader<EnregistrementSource> lecteurBrut(SourceFlux source, EtatRun etat, Options options) {
        return new ItemStreamReader<>() {
            private Iterator<EnregistrementSource> page = List.<EnregistrementSource>of().iterator();
            private String dernier = "";
            private boolean termine;

            @Override
            public EnregistrementSource read() {
                if (!page.hasNext() && !termine) {
                    List<EnregistrementSource> suivante =
                            brut.page(source.tableBrute(), source.code(), options.toutRetraiter(), dernier, tailleLot);
                    termine = suivante.size() < tailleLot;
                    page = suivante.iterator();
                }
                if (!page.hasNext()) {
                    return null;
                }
                EnregistrementSource enregistrement = page.next();
                dernier = enregistrement.sourceRecordId();
                return enregistrement;
            }
        };
    }

    /** Résultat du traitement d'un enregistrement : un flux rattaché ou un rejet motivé. */
    record Traitement(Optional<DepotFlux.FluxRattache> flux, Optional<DepotRejets.RejetMotive> rejet) {

        static Traitement flux(DepotFlux.FluxRattache flux) {
            return new Traitement(Optional.of(flux), Optional.empty());
        }

        static Traitement rejet(DepotRejets.RejetMotive rejet) {
            return new Traitement(Optional.empty(), Optional.of(rejet));
        }
    }
}
