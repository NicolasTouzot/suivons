package fr.suivons.ingestion.sirene.referentiel;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static fr.suivons.db.jooq.core.Tables.ENTREPRISE;
import static fr.suivons.db.jooq.core.Tables.FLUX;
import static fr.suivons.db.jooq.ops.Tables.INGESTION_RUN;
import static fr.suivons.db.jooq.ops.Tables.REJET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
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

import fr.suivons.ingestion.core.pipeline.PipelineIngestion.Resultat;
import fr.suivons.ingestion.core.pipeline.SuiviRuns;
import fr.suivons.ingestion.sirene.Fixtures;

/**
 * Rafraîchissement du référentiel minimal contre une API Sirene simulée (WireMock), sur des réponses réelles de
 * l'INSEE (/fixtures/sirene). Référentiel initial : cinq entreprises à rafraîchir (dont une inconnue de l'INSEE) et
 * une entreprise sans flux, ancienne, à retirer.
 */
@SpringBootTest
@Testcontainers
class RafraichissementReferentielIT {

    static final String DANONE = "552032534";
    static final String CESSEE = "412565228";
    static final String ENTREPRENEUR_INDIVIDUEL = "900000001";
    static final String DIFFUSION_PARTIELLE = "900000019";
    static final String INCONNUE = "356000000";
    static final String SANS_FLUX = "542051180";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(System.getProperty("suivons.postgres.image"));

    @RegisterExtension
    static WireMockExtension sirene = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @TempDir
    static Path cache;

    @DynamicPropertySource
    static void reglages(DynamicPropertyRegistry registre) {
        registre.add("suivons.ingestion.repertoire-cache", cache::toString);
        registre.add("suivons.referentiel.sirene.url", sirene::baseUrl);
        registre.add("suivons.referentiel.sirene.cle-api", () -> "");
        registre.add("suivons.referentiel.sirene.requetes-par-minute", () -> "6000");
        registre.add("suivons.referentiel.sirene.protection.tentatives", () -> "1");
        // Disjoncteur jamais ouvert : chaque test observe l'effet de sa propre réponse simulée
        registre.add("suivons.referentiel.sirene.protection.appels-minimum", () -> "1000");
        // Trois SIREN par lot : DANONE, CESSEE, INCONNUE, puis les deux entités fictives
        registre.add("suivons.referentiel-minimal.taille-lot", () -> "3");
    }

    @Autowired
    RafraichissementReferentiel rafraichissement;

    @Autowired
    DSLContext dsl;

    @BeforeEach
    void referentielInitial() {
        dsl.deleteFrom(FLUX).execute();
        dsl.deleteFrom(ENTREPRISE).execute();
        OffsetDateTime recent = OffsetDateTime.now().minusHours(1);
        OffsetDateTime ancien = OffsetDateTime.now().minusDays(30);
        for (String siren : List.of(CESSEE, ENTREPRENEUR_INDIVIDUEL, DIFFUSION_PARTIELLE, INCONNUE)) {
            aRafraichir(siren, recent);
        }
        aRafraichir(DANONE, ancien);
        aRafraichir(SANS_FLUX, ancien);
        fluxPour(DANONE);

        sirene.resetAll();
        sirene.stubFor(post("/siren").willReturn(okJson(Fixtures.lire("sirene/unites-legales.json"))));
        sirene.stubFor(post("/siret").willReturn(okJson(Fixtures.lire("sirene/sieges.json"))));
    }

    @Test
    void rafraichitLeReferentielEtRetireLesEntreprisesSansFlux() {
        Resultat resultat = rafraichissement.executer();

        assertThat(resultat.statut()).isEqualTo(SuiviRuns.Statut.SUCCES);
        assertThat(resultat.compteurs()).isEqualTo(new SuiviRuns.Compteurs(5, 4, 1));
        assertThat(referentiel()).isEqualTo(Fixtures.lire("golden/referentiel-rafraichi.csv").strip());
        assertThat(dsl.select(REJET.SOURCE_RECORD_ID, REJET.MOTIF).from(REJET)
                .where(REJET.RUN_ID.eq(resultat.runId())).fetch())
                .extracting(r -> r.value1(), r -> r.value2())
                .containsExactly(tuple(INCONNUE, RafraichissementReferentiel.MOTIF_INTROUVABLE));
        assertThat(dsl.fetchSingle(INGESTION_RUN, INGESTION_RUN.ID.eq(resultat.runId())).getSourceCode())
                .isEqualTo(RafraichissementReferentiel.SOURCE);
    }

    @Test
    void rejouerLeRafraichissementNeChangeRien() {
        rafraichissement.executer();
        String apresPremier = referentiel();

        Resultat second = rafraichissement.executer();

        assertThat(second.statut()).isEqualTo(SuiviRuns.Statut.SUCCES);
        assertThat(second.compteurs()).isEqualTo(new SuiviRuns.Compteurs(5, 0, 1));
        assertThat(referentiel()).isEqualTo(apresPremier);
    }

    @Test
    void quotaEpuiseArreteLeRunEnEchecSansRienModifier() {
        sirene.stubFor(post("/siren").willReturn(aResponse().withStatus(429).withHeader("Retry-After", "3600")));
        String avant = referentiel();

        Resultat resultat = rafraichissement.executer();

        assertThat(resultat.statut()).isEqualTo(SuiviRuns.Statut.ECHEC);
        assertThat(referentielSansRetrait(avant)).isEqualTo(referentiel());
        assertThat(dsl.fetchSingle(INGESTION_RUN, INGESTION_RUN.ID.eq(resultat.runId())).getStatut())
                .isEqualTo("ECHEC");
    }

    @Test
    void apiIndisponibleEnCoursDeRunConserveLesLotsDejaTraites() {
        sirene.stubFor(post("/siren").withRequestBody(containing("siren%3A" + ENTREPRENEUR_INDIVIDUEL))
                .willReturn(aResponse().withStatus(503)));

        Resultat resultat = rafraichissement.executer();

        assertThat(resultat.statut()).isEqualTo(SuiviRuns.Statut.ECHEC);
        assertThat(denomination(DANONE)).isEqualTo("DANONE");
        assertThat(dsl.fetchSingle(ENTREPRISE, ENTREPRISE.SIREN.eq(CESSEE)).getEtat()).isEqualTo("CESSEE");
        assertThat(denomination(ENTREPRENEUR_INDIVIDUEL)).isEqualTo("A RAFRAICHIR");
    }

    private void aRafraichir(String siren, OffsetDateTime rafraichiLe) {
        dsl.insertInto(ENTREPRISE)
                .set(ENTREPRISE.SIREN, siren)
                .set(ENTREPRISE.DENOMINATION, "A RAFRAICHIR")
                .set(ENTREPRISE.ETAT, "ACTIVE")
                .set(ENTREPRISE.DIFFUSIBLE, true)
                .set(ENTREPRISE.PERSONNE_PHYSIQUE, false)
                .set(ENTREPRISE.RAFRAICHI_LE, rafraichiLe)
                .execute();
    }

    private void fluxPour(String siren) {
        long runId = dsl.insertInto(INGESTION_RUN).set(INGESTION_RUN.SOURCE_CODE, "DECP")
                .returning(INGESTION_RUN.ID).fetchSingle(INGESTION_RUN.ID);
        dsl.execute("""
                INSERT INTO core.flux (canal, beneficiaire_siren, date_flux, annee, montant_ferme, montant_plafond,
                    nature_montant, rattachement, confiance_rattachement, source_code, source_record_id, source_url,
                    run_id, extrait_le)
                VALUES ('MARCHE', ?, DATE '2025-03-01', 2025, 1000, 1000, 'FERME', 'SIREN_SOURCE', 1, 'DECP', 'M1',
                    'https://example.org/M1', ?, now())
                """, siren, runId);
    }

    private String denomination(String siren) {
        return dsl.fetchSingle(ENTREPRISE, ENTREPRISE.SIREN.eq(siren)).getDenomination();
    }

    /** Contenu du référentiel (hors date de rafraîchissement), une ligne par entreprise, au format du golden file. */
    private String referentiel() {
        return dsl.selectFrom(ENTREPRISE).orderBy(ENTREPRISE.SIREN).fetch().stream()
                .map(e -> String.join(";", e.getSiren(), texte(e.getDenomination()), texte(e.getNafCode()),
                        texte(e.getNafNomenclature()), texte(e.getCommuneSiege()), texte(e.getDepartementSiege()),
                        e.getEtat(), String.valueOf(e.getDiffusible()), String.valueOf(e.getPersonnePhysique())))
                .collect(Collectors.joining("\n"));
    }

    /** Référentiel attendu après la seule purge (qui précède tout appel à l'API). */
    private static String referentielSansRetrait(String avant) {
        return avant.lines().filter(l -> !l.startsWith(SANS_FLUX)).collect(Collectors.joining("\n"));
    }

    private static String texte(String valeur) {
        return Objects.toString(valeur, "");
    }
}
