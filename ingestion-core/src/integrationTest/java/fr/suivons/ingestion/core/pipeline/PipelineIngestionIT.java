package fr.suivons.ingestion.core.pipeline;

import static fr.suivons.db.jooq.core.Tables.FLUX;
import static fr.suivons.db.jooq.ops.Tables.INGESTION_RUN;
import static fr.suivons.db.jooq.ops.Tables.REJET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import fr.suivons.domain.FluxNormalise;
import fr.suivons.ingestion.core.source.RafraichissementMart;
import fr.suivons.ingestion.core.source.Rattachement;
import fr.suivons.ingestion.core.source.Rattacheur;

/** Pipeline commun (SPEC.md §7.3) éprouvé de bout en bout sur PostgreSQL avec une source fictive. */
@SpringBootTest
@Testcontainers
class PipelineIngestionIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(System.getProperty("suivons.postgres.image"));

    @TempDir
    static Path cache;

    @DynamicPropertySource
    static void reglages(DynamicPropertyRegistry registre) {
        registre.add("suivons.ingestion.repertoire-cache", cache::toString);
        registre.add("suivons.ingestion.taille-lot", () -> "2");
    }

    @Autowired
    PipelineIngestion pipeline;

    @Autowired
    DSLContext dsl;

    final SourceDeTest source = new SourceDeTest();
    final AtomicInteger rafraichissements = new AtomicInteger();
    final RafraichissementMart mart = rafraichissements::incrementAndGet;

    /** Rattache par le SIREN de la source (les entreprises du test existent dans core.entreprise). */
    final Rattacheur rattacheur = (FluxNormalise flux) -> flux.sirenSource()
            .map(Rattachement::sirenSource)
            .orElseGet(Rattachement::nonResolu);

    @BeforeEach
    void schemaDeLaSourceFictive() {
        dsl.execute("""
                INSERT INTO ops.source (code, libelle, producteur, url_reference, frequence)
                VALUES ('TEST', 'Source fictive', 'Tests', 'https://source.test', 'À la demande')
                ON CONFLICT (code) DO NOTHING""");
        dsl.execute("""
                CREATE TABLE IF NOT EXISTS raw.test_record (
                    source_record_id text PRIMARY KEY,
                    run_id bigint NOT NULL REFERENCES ops.ingestion_run (id),
                    payload jsonb NOT NULL,
                    checksum text NOT NULL,
                    recu_le timestamptz NOT NULL)""");
        dsl.execute("""
                INSERT INTO core.entreprise (siren, denomination, etat, diffusible, personne_physique, rafraichi_le)
                VALUES ('552032534', 'DANONE', 'ACTIVE', true, false, now()),
                       ('412565228', 'SALOME INFORMATIQUE', 'CESSEE', true, false, now())
                ON CONFLICT (siren) DO NOTHING""");
        dsl.execute("TRUNCATE core.flux, ops.rejet, raw.test_record");
        dsl.execute("DELETE FROM ops.ingestion_run WHERE source_code = 'TEST'");
    }

    @Test
    void chargeLesFluxAvecLeurProvenanceEtTraceLesRejets() {
        PipelineIngestion.Resultat resultat = lancer();

        assertThat(resultat.statut()).isEqualTo(SuiviRuns.Statut.SUCCES);
        assertThat(resultat.compteurs()).isEqualTo(new SuiviRuns.Compteurs(4, 3, 1));
        assertThat(dsl.select(FLUX.SOURCE_RECORD_ID, FLUX.MONTANT_FERME, FLUX.MONTANT_PLAFOND, FLUX.NATURE_MONTANT,
                        FLUX.RATTACHEMENT, FLUX.BENEFICIAIRE_SIREN, FLUX.RUN_ID, FLUX.SOURCE_URL)
                .from(FLUX).orderBy(FLUX.SOURCE_RECORD_ID).fetch().map(r -> r.intoList()))
                .containsExactly(
                        java.util.List.of("R1", 120000L, 120000L, "FERME", "SIREN_SOURCE", "552032534",
                                resultat.runId(), "https://source.test/R1"),
                        java.util.Arrays.asList("R2", null, 50000L, "PLAFOND", "NON_RESOLU", null,
                                resultat.runId(), "https://source.test/R2"),
                        java.util.List.of("R3", 8000L, 8000L, "FERME", "SIREN_SOURCE", "412565228",
                                resultat.runId(), "https://source.test/R3"));
        assertThat(dsl.select(REJET.SOURCE_RECORD_ID, REJET.MOTIF).from(REJET).fetch())
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.value1()).isEqualTo("R4");
                    assertThat(r.value2()).contains("négatif");
                });
        assertThat(dsl.fetchCount(dsl.selectFrom("raw.test_record"))).isEqualTo(4);
        assertThat(dsl.select(INGESTION_RUN.STATUT, INGESTION_RUN.LUS, INGESTION_RUN.CHARGES, INGESTION_RUN.REJETES)
                .from(INGESTION_RUN).where(INGESTION_RUN.ID.eq(resultat.runId())).fetchOne().intoList())
                .containsExactly("SUCCES", 4, 3, 1);
        assertThat(rafraichissements).hasValue(1);
    }

    @Test
    void rejouerLeMemeFichierNeChangeRien() {
        long premierRun = lancer().runId();
        String etatAvant = etatDesFlux();

        PipelineIngestion.Resultat rejeu = lancer();

        assertThat(rejeu.rienAFaire()).isTrue();
        assertThat(rejeu.statut()).isEqualTo(SuiviRuns.Statut.SUCCES);
        assertThat(rejeu.compteurs().charges()).isZero();
        assertThat(etatDesFlux()).isEqualTo(etatAvant);
        assertThat(dsl.selectDistinct(FLUX.RUN_ID).from(FLUX).fetch(FLUX.RUN_ID)).containsExactly(premierRun);
        assertThat(rafraichissements).as("le mart n'est rafraîchi que si quelque chose a changé").hasValue(1);
    }

    @Test
    void retraiterToutSansChangementNeReecritAucunFlux() {
        long premierRun = lancer().runId();
        String etatAvant = etatDesFlux();

        PipelineIngestion.Resultat retraitement = pipeline.executer(source, rattacheur, mart,
                new PipelineIngestion.Options(true));

        assertThat(retraitement.rienAFaire()).isFalse();
        assertThat(retraitement.compteurs().charges()).isZero();
        assertThat(etatDesFlux()).isEqualTo(etatAvant);
        assertThat(dsl.selectDistinct(FLUX.RUN_ID).from(FLUX).fetch(FLUX.RUN_ID)).containsExactly(premierRun);
    }

    @Test
    void neRetraiteQueLesEnregistrementsModifiesEtSuitUnChangementDAnnee() {
        long premierRun = lancer().runId();
        source.version = "version-2.csv";

        PipelineIngestion.Resultat second = lancer();

        assertThat(second.compteurs().lus()).isEqualTo(5);
        assertThat(second.compteurs().charges()).as("R1 modifié, R2 change d'année, R5 nouveau").isEqualTo(3);
        assertThat(second.compteurs().rejetes()).as("R4 inchangé n'est pas retraité").isZero();
        assertThat(dsl.select(FLUX.SOURCE_RECORD_ID, FLUX.ANNEE, FLUX.MONTANT_PLAFOND, FLUX.RUN_ID)
                .from(FLUX).orderBy(FLUX.SOURCE_RECORD_ID).fetch().map(r -> r.intoList()))
                .containsExactly(
                        java.util.List.of("R1", (short) 2024, 150000L, second.runId()),
                        java.util.List.of("R2", (short) 2023, 50000L, second.runId()),
                        java.util.List.of("R3", (short) 2025, 8000L, premierRun),
                        java.util.List.of("R5", (short) 2026, 9000L, second.runId()));
        assertThat(dsl.fetchCount(FLUX, FLUX.SOURCE_RECORD_ID.eq("R2")))
                .as("une seule ligne par enregistrement source, toutes années confondues").isEqualTo(1);
    }

    @Test
    void unEchecEstTraceEtNeRafraichitPasLeMart() {
        lancer();
        String etatAvant = etatDesFlux();
        source.version = "version-2.csv";
        source.enPanne = true;

        PipelineIngestion.Resultat echec = lancer();

        assertThat(echec.statut()).isEqualTo(SuiviRuns.Statut.ECHEC);
        assertThat(dsl.select(INGESTION_RUN.STATUT).from(INGESTION_RUN).where(INGESTION_RUN.ID.eq(echec.runId()))
                .fetchOne(INGESTION_RUN.STATUT)).isEqualTo("ECHEC");
        assertThat(etatDesFlux()).isEqualTo(etatAvant);
        assertThat(rafraichissements).hasValue(1);
    }

    @Test
    void unFichierIdentiqueAUnRunEchoueEstRetraite() {
        source.enPanne = true;
        lancer();
        source.enPanne = false;

        PipelineIngestion.Resultat reprise = lancer();

        assertThat(reprise.rienAFaire()).as("seul un run réussi fait foi pour le checksum").isFalse();
        assertThat(reprise.compteurs().charges()).isEqualTo(3);
    }

    @Test
    void rattacheParLotEtUnEchecDuRattachementEstRetraiteAuRunSuivant() {
        java.util.List<Integer> lots = new java.util.ArrayList<>();
        Rattacheur enPanne = new Rattacheur() {
            @Override
            public Rattachement rattacher(FluxNormalise flux) {
                throw new UnsupportedOperationException("appel unitaire inattendu");
            }

            @Override
            public java.util.List<Rattachement> rattacherLot(java.util.List<FluxNormalise> lot) {
                throw new IllegalStateException("API d'appui indisponible");
            }
        };
        Rattacheur parLot = new Rattacheur() {
            @Override
            public Rattachement rattacher(FluxNormalise flux) {
                throw new UnsupportedOperationException("appel unitaire inattendu");
            }

            @Override
            public java.util.List<Rattachement> rattacherLot(java.util.List<FluxNormalise> lot) {
                lots.add(lot.size());
                return lot.stream().map(rattacheur::rattacher).toList();
            }
        };

        PipelineIngestion.Resultat echec = pipeline.executer(source, enPanne, mart,
                PipelineIngestion.Options.PAR_DEFAUT);
        PipelineIngestion.Resultat reprise = pipeline.executer(source, parLot, mart,
                PipelineIngestion.Options.PAR_DEFAUT);

        assertThat(echec.statut()).isEqualTo(SuiviRuns.Statut.ECHEC);
        assertThat(reprise.statut()).isEqualTo(SuiviRuns.Statut.SUCCES);
        assertThat(reprise.compteurs().charges()).isEqualTo(3);
        assertThat(lots).as("lots de 2 enregistrements, rejet exclu").containsExactly(2, 1);
    }

    @Test
    void prepareLeRattachementAvecTousLesSirenDistinctsAvantLaTransformation() {
        java.util.List<String> evenements = new java.util.ArrayList<>();
        Rattacheur prepare = new Rattacheur() {
            @Override
            public Rattachement rattacher(FluxNormalise flux) {
                return rattacheur.rattacher(flux);
            }

            @Override
            public java.util.List<Rattachement> rattacherLot(java.util.List<FluxNormalise> lot) {
                evenements.add("lot");
                return Rattacheur.super.rattacherLot(lot);
            }

            @Override
            public void preparer(java.util.List<fr.suivons.domain.Siren> sirens) {
                evenements.add("préparation " + sirens.stream().map(fr.suivons.domain.Siren::valeur).toList());
            }
        };

        pipeline.executer(source, prepare, mart, PipelineIngestion.Options.PAR_DEFAUT);

        assertThat(evenements).as("SIREN distincts par lots de 2, tous avant le premier lot de flux")
                .containsExactly("préparation [412565228, 552032534]", "lot", "lot");
    }

    private PipelineIngestion.Resultat lancer() {
        return pipeline.executer(source, rattacheur, mart, PipelineIngestion.Options.PAR_DEFAUT);
    }

    private String etatDesFlux() {
        return dsl.selectFrom(FLUX).orderBy(FLUX.SOURCE_RECORD_ID).fetch()
                .map(r -> r.into(FLUX.SOURCE_RECORD_ID, FLUX.ANNEE, FLUX.MONTANT_FERME, FLUX.MONTANT_PLAFOND,
                        FLUX.RATTACHEMENT, FLUX.RUN_ID, FLUX.EXTRAIT_LE).toString())
                .toString();
    }
}
