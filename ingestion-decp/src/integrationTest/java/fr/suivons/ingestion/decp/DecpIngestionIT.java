package fr.suivons.ingestion.decp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static fr.suivons.db.jooq.core.Tables.ENTREPRISE;
import static fr.suivons.db.jooq.core.Tables.FLUX;
import static fr.suivons.db.jooq.core.Tables.PAYEUR;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import fr.suivons.ingestion.core.pipeline.PipelineIngestion;
import fr.suivons.ingestion.core.pipeline.SuiviRuns;
import fr.suivons.ingestion.core.source.RafraichissementMart;

/**
 * Ingestion DECP de bout en bout sur un extrait réel de juin 2026 (/fixtures/decp, identifiants des titulaires
 * remplacés, au format Parquet de l'export réel), export et API Sirene simulés par WireMock : fusion, bornes,
 * rattachement par SIRET (SIREN lus dans Sirene en une fois), idempotence.
 */
@SpringBootTest
@Testcontainers
class DecpIngestionIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(System.getProperty("suivons.postgres.image"));

    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @TempDir
    static Path cache;

    @DynamicPropertySource
    static void reglages(DynamicPropertyRegistry registre) {
        registre.add("suivons.ingestion.repertoire-cache", cache::toString);
        registre.add("suivons.decp.jeu", () -> api.baseUrl() + "/decp");
        registre.add("suivons.referentiel.sirene.url", () -> api.baseUrl() + "/sirene");
        registre.add("suivons.referentiel.sirene.cle-api", () -> "");
        registre.add("suivons.referentiel.sirene.requetes-par-minute", () -> "6000");
        registre.add("suivons.referentiel.sirene.protection.tentatives", () -> "1");
        registre.add("suivons.referentiel.sirene.protection.appels-minimum", () -> "1000");
    }

    @Autowired
    PipelineIngestion pipeline;

    @Autowired
    SourceDecp source;

    @Autowired
    RattacheurSiret rattacheur;

    @Autowired
    DSLContext dsl;

    @BeforeEach
    void apiSimulees() {
        dsl.execute("TRUNCATE core.flux, core.entreprise, core.payeur, raw.decp_record, ops.rejet, ops.ingestion_run "
                + "CASCADE");
        api.resetAll();
        // Jeu entièrement indexé chez le producteur
        api.stubFor(get(urlPathEqualTo("/decp"))
                .willReturn(okJson("{\"metas\": {\"default\": {\"records_count\": 708152, "
                        + "\"modified\": \"2026-09-22T14:40:23+00:00\"}}}")));
        api.stubFor(get(urlPathEqualTo("/decp/records")).willReturn(okJson("{\"total_count\": 708152}")));
        api.stubFor(get(urlPathEqualTo("/decp/exports/parquet"))
                .willReturn(aResponse().withBody(octets("decp/decp-2022-juin-2026-extrait.parquet"))));
        api.stubFor(post("/sirene/siren").willReturn(okJson(fixture("sirene/unites-legales.json"))));
        api.stubFor(post("/sirene/siret").willReturn(okJson(fixture("sirene/sieges.json"))));
    }

    @Test
    void chargeLeMoisEtRattacheParSiret() {
        PipelineIngestion.Resultat resultat = lancer();

        assertThat(resultat.statut()).isEqualTo(SuiviRuns.Statut.SUCCES);
        assertThat(resultat.compteurs()).isEqualTo(new SuiviRuns.Compteurs(15, 15, 0));
        assertThat(flux()).isEqualTo(fixture("golden/decp-flux.csv").strip());
        assertThat(dsl.select(ENTREPRISE.SIREN, ENTREPRISE.DENOMINATION).from(ENTREPRISE).orderBy(ENTREPRISE.SIREN)
                .fetch().map(r -> r.value1() + ";" + Objects.toString(r.value2(), "")))
                .as("entreprises ajoutées au référentiel ; aucune dénomination pour l'EI ni la diffusion partielle")
                .containsExactly("412565228;SALOME INFORMATIQUE", "552032534;DANONE", "900000001;", "900000019;");
        assertThat(dsl.fetchCount(PAYEUR)).isEqualTo(11);
        api.verify(1, getRequestedFor(urlPathEqualTo("/decp/exports/parquet")));
        api.verify(1, postRequestedFor(urlPathEqualTo("/sirene/siren")));
    }

    @Test
    void rejouerLeMemeMoisNeChangeRien() {
        lancer();
        String avant = flux();

        PipelineIngestion.Resultat rejeu = lancer();
        PipelineIngestion.Resultat retraitement = pipeline.executer(source, rattacheur, RafraichissementMart.AUCUN,
                new PipelineIngestion.Options(true));

        assertThat(rejeu.rienAFaire()).isTrue();
        assertThat(retraitement.statut()).isEqualTo(SuiviRuns.Statut.SUCCES);
        assertThat(retraitement.compteurs().charges()).isZero();
        assertThat(flux()).isEqualTo(avant);
    }

    @Test
    void sireneIndisponibleFaitEchouerLeRunQuiEstRepriseEnsuite() {
        api.stubFor(post("/sirene/siren").willReturn(aResponse().withStatus(503)));

        PipelineIngestion.Resultat echec = lancer();

        assertThat(echec.statut()).isEqualTo(SuiviRuns.Statut.ECHEC);
        assertThat(dsl.fetchCount(FLUX)).as("aucun flux rattaché à tort comme non résolu").isZero();

        api.stubFor(post("/sirene/siren").willReturn(okJson(fixture("sirene/unites-legales.json"))));
        PipelineIngestion.Resultat reprise = lancer();

        assertThat(reprise.statut()).isEqualTo(SuiviRuns.Statut.SUCCES);
        assertThat(reprise.rienAFaire()).isFalse();
        assertThat(flux()).isEqualTo(fixture("golden/decp-flux.csv").strip());
    }

    @Test
    void reporteLExportPendantUneReindexationChezLeProducteur() {
        api.stubFor(get(urlPathEqualTo("/decp/records")).willReturn(okJson("{\"total_count\": 261529}")));

        PipelineIngestion.Resultat resultat = lancer();

        assertThat(resultat.statut()).isEqualTo(SuiviRuns.Statut.ECHEC);
        assertThat(dsl.fetchCount(FLUX)).isZero();
        api.verify(0, getRequestedFor(urlPathEqualTo("/decp/exports/parquet")));
    }

    private PipelineIngestion.Resultat lancer() {
        return pipeline.executer(source, rattacheur, RafraichissementMart.AUCUN, PipelineIngestion.Options.PAR_DEFAUT);
    }

    /** Flux chargés, au format du golden file (hors run et date d'extraction). */
    private String flux() {
        return dsl.select(FLUX.SOURCE_RECORD_ID, FLUX.BENEFICIAIRE_SIREN, FLUX.RATTACHEMENT, FLUX.NATURE_MONTANT,
                        FLUX.MONTANT_FERME, FLUX.MONTANT_PLAFOND, FLUX.QUALITE, FLUX.DATE_FLUX, PAYEUR.IDENTIFIANT)
                .from(FLUX).leftJoin(PAYEUR).on(PAYEUR.ID.eq(FLUX.PAYEUR_ID))
                .orderBy(FLUX.SOURCE_RECORD_ID)
                .fetch().stream()
                .map(r -> r.intoStream().map(v -> Objects.toString(v, "")).collect(Collectors.joining(";")))
                .collect(Collectors.joining("\n"));
    }

    private static String fixture(String chemin) {
        return new String(octets(chemin), StandardCharsets.UTF_8);
    }

    private static byte[] octets(String chemin) {
        try (InputStream flux = DecpIngestionIT.class.getResourceAsStream("/" + chemin)) {
            if (flux == null) {
                throw new IllegalArgumentException("Fixture introuvable : " + chemin);
            }
            return flux.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
