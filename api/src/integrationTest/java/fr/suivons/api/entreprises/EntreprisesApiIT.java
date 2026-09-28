package fr.suivons.api.entreprises;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F2 et F3 sur PostgreSQL : montants hors flux aberrants, masquage des entreprises non nommables, erreurs RFC 9457,
 * pagination. Chaque réponse est validée contre le contrat (openapi.yaml).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EntreprisesApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(System.getProperty("suivons.postgres.image"));

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static OpenApiInteractionValidator contrat;

    @Value("${local.server.port}")
    int port;

    @Autowired
    DSLContext dsl;

    @BeforeAll
    static void chargerLeContrat() throws IOException {
        try (InputStream flux = EntreprisesApiIT.class.getResourceAsStream("/openapi/openapi.yaml")) {
            contrat = OpenApiInteractionValidator
                    .createForInlineApiSpecification(new String(flux.readAllBytes(), StandardCharsets.UTF_8))
                    .build();
        }
    }

    @BeforeEach
    void donnees() {
        dsl.execute("TRUNCATE core.flux, core.entreprise, core.payeur, core.naf, ops.ingestion_run CASCADE");
        dsl.execute("""
                INSERT INTO core.naf (nomenclature, code, libelle)
                VALUES ('NAFRev2', '70.10Z', 'Activités des sièges sociaux')""");
        dsl.execute("""
                INSERT INTO core.entreprise (siren, denomination, naf_code, naf_nomenclature, commune_siege,
                    departement_siege, etat, diffusible, personne_physique, rafraichi_le) VALUES
                ('552032534', 'DANONE', '70.10Z', 'NAFRev2', 'PARIS', '75', 'ACTIVE', true, false, now()),
                ('900000001', 'NOM D''UNE PERSONNE', '43.91A', 'NAFRev2', 'COMMUNE FICTIVE', null, 'ACTIVE', true,
                    true, now()),
                ('900000019', 'DENOMINATION FICTIVE', null, null, null, null, 'ACTIVE', false, false,
                    '2026-09-01T00:00:00Z')""");
        dsl.execute("INSERT INTO core.payeur (identifiant, nom, type) VALUES ('21280201100016', null, 'AUTRE')");
        long run = dsl.fetchSingle("INSERT INTO ops.ingestion_run (source_code) VALUES ('DECP') RETURNING id")
                .get(0, Long.class);
        // Danone : un marché ferme, un accord-cadre (plafond), un groupement (partagé), un montant aberrant
        flux(run, "M1", "552032534", "2025-03-10", 100000L, 100000L, "FERME", "OK");
        flux(run, "M2", "552032534", "2026-06-04", null, 500000L, "PLAFOND", "OK");
        flux(run, "M3", "552032534", "2026-06-20", null, 300000L, "PARTAGE", "OK");
        flux(run, "M4", "552032534", "2026-06-29", 1L, 1L, "FERME", "ABERRANT");
        flux(run, "M5", "900000001", "2026-06-12", 42000L, 42000L, "FERME", "OK");
    }

    @Test
    void syntheseDUneEntreprise() throws Exception {
        JsonNode synthese = lire(get("/api/v1/entreprises/552032534"), 200);

        assertThat(synthese.path("denomination").asString()).isEqualTo("DANONE");
        assertThat(synthese.path("nomMasque").asBoolean()).isFalse();
        assertThat(synthese.path("activite").path("libelle").asString()).isEqualTo("Activités des sièges sociaux");
        assertThat(synthese.path("commune").asString()).isEqualTo("PARIS");
        assertThat(synthese.path("montants").path("montantFerme").asLong())
                .as("borne ferme : seul le marché ferme, hors aberrant").isEqualTo(100000L);
        assertThat(synthese.path("montants").path("montantPlafond").asLong()).isEqualTo(900000L);
        assertThat(synthese.path("nombreFlux").asInt()).isEqualTo(3);
        assertThat(synthese.path("nombreFluxAberrants").asInt()).isEqualTo(1);
        assertThat(synthese.path("periode").path("premiereAnnee").asInt()).isEqualTo(2025);
        assertThat(synthese.path("periode").path("derniereAnnee").asInt()).isEqualTo(2026);
        assertThat(synthese.path("canaux")).hasSize(1);
        assertThat(synthese.path("canaux").get(0).path("canal").asString()).isEqualTo("MARCHE");
        assertThat(synthese.path("lienAnnuaire").asString())
                .isEqualTo("https://annuaire-entreprises.data.gouv.fr/entreprise/552032534");
        assertThat(synthese.path("derniereMiseAJour").asString()).isNotBlank();
    }

    @Test
    void aucuneDenominationPourUnEntrepreneurIndividuelOuUneEntrepriseNonDiffusible() throws Exception {
        JsonNode individuel = lire(get("/api/v1/entreprises/900000001"), 200);
        JsonNode nonDiffusible = lire(get("/api/v1/entreprises/900000019"), 200);

        assertThat(individuel.path("nomMasque").asBoolean()).isTrue();
        assertThat(individuel.path("denomination").isMissingNode() || individuel.path("denomination").isNull())
                .isTrue();
        assertThat(nonDiffusible.path("nomMasque").asBoolean()).isTrue();
        assertThat(nonDiffusible.path("denomination").isMissingNode() || nonDiffusible.path("denomination").isNull())
                .isTrue();
        assertThat(nonDiffusible.path("nombreFlux").asInt()).isZero();
        assertThat(nonDiffusible.path("montants").path("montantPlafond").asLong()).isZero();
        assertThat(nonDiffusible.path("periode").isMissingNode() || nonDiffusible.path("periode").isNull()).isTrue();
    }

    @Test
    void erreursAuFormatProblemDetails() throws Exception {
        HttpResponse<String> inconnue = get("/api/v1/entreprises/356000000");
        HttpResponse<String> cleInvalide = get("/api/v1/entreprises/552032535");
        HttpResponse<String> format = get("/api/v1/entreprises/ABC");
        HttpResponse<String> tailleExcessive = get("/api/v1/entreprises/552032534/flux?size=101");

        assertThat(lire(inconnue, 404).path("detail").asString()).contains("Aucun flux tracé");
        assertThat(lire(cleInvalide, 400).path("detail").asString()).contains("SIREN invalide");
        lire(format, 400);
        lire(tailleExcessive, 400);
        assertThat(inconnue.headers().firstValue("Content-Type")).hasValue("application/problem+json");
    }

    @Test
    void fluxDuPlusRecentAuPlusAncienAvecLeurSource() throws Exception {
        JsonNode page1 = lire(get("/api/v1/entreprises/552032534/flux?size=3"), 200);
        JsonNode page2 = lire(get("/api/v1/entreprises/552032534/flux?size=3&page=2"), 200);

        assertThat(page1.path("total").asLong()).isEqualTo(4);
        assertThat(page1.path("elements")).extracting(f -> f.path("identifiant").asString())
                .containsExactly("M4", "M3", "M2");
        assertThat(page2.path("elements")).extracting(f -> f.path("identifiant").asString()).containsExactly("M1");
        JsonNode aberrant = page1.path("elements").get(0);
        assertThat(aberrant.path("qualite").asString()).as("visible dans le détail").isEqualTo("ABERRANT");
        JsonNode plafond = page1.path("elements").get(2);
        assertThat(plafond.path("montantFerme").isMissingNode() || plafond.path("montantFerme").isNull()).isTrue();
        assertThat(plafond.path("montantPlafond").asLong()).isEqualTo(500000L);
        assertThat(plafond.path("payeur").path("identifiant").asString()).isEqualTo("21280201100016");
        assertThat(plafond.path("source").path("code").asString()).isEqualTo("DECP");
        assertThat(plafond.path("source").path("libelle").asString())
                .isEqualTo("Données essentielles de la commande publique");
        assertThat(plafond.path("source").path("url").asString()).isEqualTo("https://decp.test/M2");
    }

    private void flux(long run, String id, String siren, String date, Long ferme, Long plafond, String nature,
            String qualite) {
        dsl.execute("""
                INSERT INTO core.flux (canal, beneficiaire_siren, payeur_id, objet, date_flux, annee, montant_ferme,
                    montant_plafond, nature_montant, qualite, rattachement, confiance_rattachement, source_code,
                    source_record_id, source_url, run_id, extrait_le)
                VALUES ('MARCHE', ?, (SELECT id FROM core.payeur), 'Objet ' || ?, ?::date, extract(YEAR FROM ?::date),
                    ?, ?, ?, ?, 'SIREN_SOURCE', 1, 'DECP', ?, 'https://decp.test/' || ?, ?, now())""",
                siren, id, date, date, ferme, plafond, nature, qualite, id, id, run);
    }

    private HttpResponse<String> get(String chemin) throws IOException, InterruptedException {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Vérifie le statut, valide l'échange contre le contrat, puis lit le corps. */
    private JsonNode lire(HttpResponse<String> reponse, int statut) {
        assertThat(reponse.statusCode()).as(reponse.body()).isEqualTo(statut);
        URI uri = reponse.uri();
        SimpleRequest.Builder requete = SimpleRequest.Builder.get(uri.getPath());
        if (uri.getQuery() != null) {
            for (String parametre : uri.getQuery().split("&")) {
                String[] cleValeur = parametre.split("=", 2);
                requete.withQueryParam(cleValeur[0], cleValeur[1]);
            }
        }
        ValidationReport rapport = contrat.validate(requete.build(), SimpleResponse.Builder.status(statut)
                .withContentType(reponse.headers().firstValue("Content-Type").orElse(""))
                .withBody(reponse.body())
                .build());
        if (statut < 400) {
            assertThat(rapport.getMessages()).as("réponse conforme au contrat").isEmpty();
        } else {
            // Les requêtes invalides le sont aussi pour le validateur : seule la réponse est jugée
            assertThat(rapport.getMessages()).filteredOn(m -> m.getKey().startsWith("validation.response"))
                    .as("réponse d'erreur conforme au contrat").isEmpty();
        }
        return JSON.readTree(reponse.body());
    }
}
