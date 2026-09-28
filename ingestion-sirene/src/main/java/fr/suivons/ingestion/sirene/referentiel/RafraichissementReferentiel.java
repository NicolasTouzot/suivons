package fr.suivons.ingestion.sirene.referentiel;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
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
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.transaction.PlatformTransactionManager;

import fr.suivons.domain.Siren;
import fr.suivons.ingestion.core.pipeline.DepotRejets;
import fr.suivons.ingestion.core.pipeline.IngestionException;
import fr.suivons.ingestion.core.pipeline.PipelineIngestion.Resultat;
import fr.suivons.ingestion.core.pipeline.SuiviRuns;
import fr.suivons.referentiel.sirene.Siege;
import fr.suivons.referentiel.sirene.SireneClient;
import fr.suivons.referentiel.sirene.UniteLegale;

/**
 * Rafraîchissement hebdomadaire du référentiel minimal via l'API Sirene (source `SIRENE`, SPEC.md §6.3), sous forme
 * de job Spring Batch : retrait des entreprises sans flux, puis relecture de toutes les autres par lots (unités
 * légales et sièges, requêtes groupées), une transaction par lot. Un quota épuisé ou une API indisponible arrête
 * le run en échec ; les lots déjà traités restent acquis et le run suivant reprend tout.
 */
public class RafraichissementReferentiel {

    public static final String SOURCE = "SIRENE";
    static final String MOTIF_INTROUVABLE = "SIREN introuvable dans l'API Sirene";
    private static final Logger LOG = LoggerFactory.getLogger(RafraichissementReferentiel.class);

    private final JobRepository jobRepository;
    private final JobOperator jobOperator;
    private final PlatformTransactionManager transactions;
    private final SuiviRuns runs;
    private final DepotEntreprises entreprises;
    private final EntreprisesSansFlux sansFlux;
    private final DepotRejets rejets;
    private final SireneClient sirene;
    private final ReferentielMinimalProperties reglages;

    public RafraichissementReferentiel(JobRepository jobRepository, JobOperator jobOperator,
            PlatformTransactionManager transactions, SuiviRuns runs, DepotEntreprises entreprises,
            EntreprisesSansFlux sansFlux, DepotRejets rejets, SireneClient sirene,
            ReferentielMinimalProperties reglages) {
        this.jobRepository = jobRepository;
        this.jobOperator = jobOperator;
        this.transactions = transactions;
        this.runs = runs;
        this.entreprises = entreprises;
        this.sansFlux = sansFlux;
        this.rejets = rejets;
        this.sirene = sirene;
        this.reglages = reglages;
    }

    public Resultat executer() {
        Etat etat = new Etat();
        try {
            JobExecution execution = jobOperator.start(job(etat), new JobParametersBuilder()
                    .addString("source", SOURCE)
                    .addLong("lancement", System.nanoTime())
                    .toJobParameters());
            return new Resultat(etat.runId, statut(execution), false, etat.compteurs());
        } catch (Exception e) {
            throw new IngestionException("Lancement du rafraîchissement du référentiel impossible", e);
        }
    }

    private Job job(Etat etat) {
        Step purge = new StepBuilder("purge-referentiel", jobRepository)
                .tasklet((contribution, contexte) -> {
                    etat.retirees = entreprises.retirer(sansFlux.nonRafraichiesDepuis(etat.debut.minus(reglages.delaiGrace())));
                    return RepeatStatus.FINISHED;
                }, transactions)
                .build();

        Step rafraichissement = new StepBuilder("rafraichissement-referentiel", jobRepository)
                .<Siren, Siren>chunk(reglages.tailleLot())
                .transactionManager(transactions)
                .reader(lecteur())
                .writer(lot -> rafraichir(etat, lot))
                .build();

        return new JobBuilder("referentiel-" + SOURCE, jobRepository)
                .listener(new JobExecutionListener() {
                    @Override
                    public void beforeJob(JobExecution execution) {
                        etat.runId = runs.demarrer(SOURCE);
                        LOG.info("Référentiel : run {} démarré", etat.runId);
                    }

                    @Override
                    public void afterJob(JobExecution execution) {
                        SuiviRuns.Statut statut = statut(execution);
                        runs.terminer(etat.runId, statut, etat.compteurs());
                        LOG.info("Référentiel : run {} terminé, {} ({} lus, {} modifiées, {} introuvables, "
                                        + "{} retirées faute de flux)", etat.runId, statut, etat.lus.get(),
                                etat.charges.get(), etat.rejetes.get(), etat.retirees);
                    }
                })
                .start(purge)
                .next(rafraichissement)
                .build();
    }

    private void rafraichir(Etat etat, Chunk<? extends Siren> lot) {
        List<Siren> sirens = new ArrayList<>(lot.getItems());
        etat.lus.addAndGet(sirens.size());
        // Seules les unités demandées sont retenues, quoi que renvoie l'API
        List<UniteLegale> unites = sirene.unitesLegales(sirens).stream()
                .filter(u -> sirens.contains(u.siren()))
                .toList();
        Map<Siren, Siege> sieges = unites.isEmpty() ? Map.of()
                : sirene.sieges(unites.stream().map(UniteLegale::siren).toList()).stream()
                        .collect(Collectors.toMap(Siege::siren, Function.identity(), (a, b) -> a));
        List<EntrepriseReferentiel> lues = unites.stream()
                .map(u -> EntrepriseReferentiel.depuis(u, Optional.ofNullable(sieges.get(u.siren()))))
                .toList();
        etat.charges.addAndGet(entreprises.enregistrer(lues, OffsetDateTime.now()));

        List<Siren> trouves = unites.stream().map(UniteLegale::siren).toList();
        List<DepotRejets.RejetMotive> introuvables = sirens.stream()
                .filter(s -> !trouves.contains(s))
                .map(s -> new DepotRejets.RejetMotive(s.valeur(), MOTIF_INTROUVABLE,
                        "{\"siren\": \"" + s.valeur() + "\"}"))
                .toList();
        etat.rejetes.addAndGet(rejets.enregistrer(etat.runId, introuvables));
    }

    /** Parcourt les SIREN du référentiel par pages, dans l'ordre. */
    private ItemStreamReader<Siren> lecteur() {
        return new ItemStreamReader<>() {
            private Iterator<Siren> page = List.<Siren>of().iterator();
            private String dernier = "";
            private boolean termine;

            @Override
            public Siren read() {
                if (!page.hasNext() && !termine) {
                    List<Siren> suivante = entreprises.page(dernier, reglages.tailleLot());
                    termine = suivante.size() < reglages.tailleLot();
                    page = suivante.iterator();
                }
                if (!page.hasNext()) {
                    return null;
                }
                Siren siren = page.next();
                dernier = siren.valeur();
                return siren;
            }
        };
    }

    private static SuiviRuns.Statut statut(JobExecution execution) {
        return execution.getStatus() == BatchStatus.COMPLETED ? SuiviRuns.Statut.SUCCES : SuiviRuns.Statut.ECHEC;
    }

    /** État partagé par les étapes d'un run. */
    private static final class Etat {
        final OffsetDateTime debut = OffsetDateTime.now();
        final AtomicInteger lus = new AtomicInteger();
        final AtomicInteger charges = new AtomicInteger();
        final AtomicInteger rejetes = new AtomicInteger();
        volatile long runId;
        volatile int retirees;

        SuiviRuns.Compteurs compteurs() {
            return new SuiviRuns.Compteurs(lus.get(), charges.get(), rejetes.get());
        }
    }
}
