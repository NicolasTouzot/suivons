package fr.suivons.referentiel.sirene;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import fr.suivons.domain.Siren;
import fr.suivons.referentiel.AppelRefuseException;
import fr.suivons.referentiel.Fixtures;
import fr.suivons.referentiel.QuotaDepasseException;
import fr.suivons.referentiel.ReferentielIndisponibleException;

/** Client Sirene contre une API simulée (WireMock), sur des réponses réelles de l'INSEE (/fixtures/sirene). */
class SireneClientIT {

    private static final List<Siren> QUATRE = List.of(
            new Siren("552032534"), new Siren("900000019"), new Siren("900000001"), new Siren("412565228"));

    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Test
    void litLesUnitesLegalesParLotEtEnvoieLaCle() {
        api.stubFor(post("/siren").willReturn(okJson(Fixtures.lire("sirene/unites-legales.json"))));

        Map<String, UniteLegale> unites = client(reglages("cle-de-test", 1000, 2, Duration.ofSeconds(5)))
                .unitesLegales(QUATRE).stream()
                .collect(Collectors.toMap(u -> u.siren().valeur(), Function.identity()));

        assertThat(unites).containsOnlyKeys("552032534", "900000019", "900000001", "412565228");
        UniteLegale danone = unites.get("552032534");
        assertThat(danone.denomination()).contains("DANONE");
        assertThat(danone.diffusible()).isTrue();
        assertThat(danone.active()).isTrue();
        assertThat(danone.activitePrincipale()).contains(new ActivitePrincipale("70.10Z", "NAFRev2"));
        assertThat(danone.categorieEntreprise()).contains("GE");

        // Diffusion partielle : l'INSEE transmet la dénomination d'une personne morale ; c'est au référentiel
        // et à l'affichage de ne jamais l'exposer (SPEC §11.2), d'où l'indicateur diffusible
        assertThat(unites.get("900000019").diffusible()).isFalse();
        assertThat(unites.get("900000019").denomination()).contains("DENOMINATION FICTIVE");
        assertThat(unites.get("900000001").personnePhysique()).isTrue();
        assertThat(unites.get("412565228").active()).isFalse();

        api.verify(1, postRequestedFor(urlEqualTo("/siren"))
                .withHeader(SireneClient.ENTETE_CLE, equalTo("cle-de-test"))
                .withRequestBody(containing("siren%3A552032534+OR+siren%3A900000019")));
    }

    @Test
    void decoupeLesDemandesEnLotsSansDoublon() {
        api.stubFor(post("/siren").willReturn(okJson(Fixtures.lire("sirene/aucun-resultat.json")).withStatus(404)));
        List<Siren> cinq = new ArrayList<>(QUATRE);
        cinq.add(new Siren("552032534"));
        cinq.add(new Siren("130005481"));

        assertThat(client(reglages("", 2, 2, Duration.ofSeconds(5))).unitesLegales(cinq)).isEmpty();

        api.verify(3, postRequestedFor(urlEqualTo("/siren")));
    }

    @Test
    void renvoieUneListeVideQuandLInseeNeTrouveRien() {
        api.stubFor(post("/siren").willReturn(okJson(Fixtures.lire("sirene/aucun-resultat.json")).withStatus(404)));

        assertThat(client(reglages("", 1000, 2, Duration.ofSeconds(5))).unitesLegales(List.of(new Siren("552032534"))))
                .isEmpty();
    }

    @Test
    void litLesSiegesEtLeurDepartement() {
        api.stubFor(post("/siret").willReturn(okJson(Fixtures.lire("sirene/sieges.json"))));

        Map<String, Siege> sieges = client(reglages("", 1000, 2, Duration.ofSeconds(5))).sieges(QUATRE).stream()
                .collect(Collectors.toMap(s -> s.siren().valeur(), Function.identity()));

        assertThat(sieges.get("412565228").libelleCommune()).contains("PARIS");
        assertThat(sieges.get("412565228").departement()).contains("75");
        api.verify(postRequestedFor(urlEqualTo("/siret")).withRequestBody(containing("etablissementSiege%3Atrue")));
    }

    @Test
    void parcourtLesModificationsPageParPage() {
        api.stubFor(post("/siren").withRequestBody(containing("curseur=*"))
                .willReturn(okJson(Fixtures.lire("sirene/modifiees-page-1.json"))));
        api.stubFor(post("/siren").withRequestBody(containing("curseur=AoEpMTMwNjM1MTIx"))
                .willReturn(okJson(Fixtures.lire("sirene/modifiees-page-2.json"))));
        api.stubFor(post("/siren").withRequestBody(containing("curseur=AoEpMTYwOTk0NTY3"))
                .willReturn(okJson(Fixtures.lire("sirene/modifiees-page-3.json"))));
        List<List<UniteLegale>> pages = new ArrayList<>();

        client(reglages("", 1000, 2, Duration.ofSeconds(5)))
                .unitesLegalesModifieesDepuis(LocalDateTime.of(2026, 9, 26, 0, 0), pages::add);

        assertThat(pages).hasSize(2);
        assertThat(pages.stream().mapToInt(List::size).sum()).isEqualTo(4);
        api.verify(postRequestedFor(urlEqualTo("/siren"))
                .withRequestBody(containing("dateDernierTraitementUniteLegale%3A%5B2026-09-26T00%3A00%3A00+TO+*%5D")));
    }

    @Test
    void nEnvoieAucuneCleQuandElleNEstPasConfiguree() {
        api.stubFor(post("/siren").willReturn(okJson(Fixtures.lire("sirene/unites-legales.json"))));

        client(reglages("", 1000, 2, Duration.ofSeconds(5))).unitesLegales(QUATRE);

        api.verify(postRequestedFor(urlEqualTo("/siren")).withHeader(SireneClient.ENTETE_CLE, absent()));
    }

    @Test
    void relanceApresUn429EnRespectantRetryAfter() {
        api.stubFor(post("/siren").inScenario("quota").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0"))
                .willSetStateTo("libre"));
        api.stubFor(post("/siren").inScenario("quota").whenScenarioStateIs("libre")
                .willReturn(okJson(Fixtures.lire("sirene/unites-legales.json"))));

        assertThat(client(reglages("", 1000, 3, Duration.ofSeconds(5))).unitesLegales(QUATRE)).hasSize(4);
        api.verify(2, postRequestedFor(urlEqualTo("/siren")));
    }

    @Test
    void ouvreLeDisjoncteurApresDesErreursRepetees() {
        api.stubFor(post("/siren").willReturn(aResponse().withStatus(503)));
        SireneClient client = client(reglages("", 1000, 1, Duration.ofSeconds(5)));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> client.unitesLegales(QUATRE)).isInstanceOf(ReferentielIndisponibleException.class);
        }
        assertThatThrownBy(() -> client.unitesLegales(QUATRE))
                .isInstanceOf(ReferentielIndisponibleException.class)
                .hasMessageContaining("disjoncteur ouvert");

        assertThat(client.etatDisjoncteur()).isEqualTo("OPEN");
        api.verify(2, postRequestedFor(urlEqualTo("/siren")));
    }

    @Test
    void signaleUneIndisponibiliteQuandLApiEstTropLente() {
        api.stubFor(post("/siren").willReturn(okJson(Fixtures.lire("sirene/unites-legales.json")).withFixedDelay(2_000)));

        assertThatThrownBy(() -> client(reglages("", 1000, 1, Duration.ofMillis(300))).unitesLegales(QUATRE))
                .isInstanceOf(ReferentielIndisponibleException.class);
    }

    @Test
    void cesseDAppelerQuandLeQuotaHoraireEstEpuise() {
        long remiseAZero = Instant.now().plus(Duration.ofHours(1)).toEpochMilli();
        api.stubFor(post("/siren").willReturn(okJson(Fixtures.lire("sirene/unites-legales.json"))
                .withHeader("x-quota-remaining", "0")
                .withHeader("x-quota-reset", String.valueOf(remiseAZero))));
        SireneClient client = client(reglages("", 1000, 3, Duration.ofSeconds(5)));

        assertThat(client.unitesLegales(QUATRE)).hasSize(4);
        assertThatThrownBy(() -> client.unitesLegales(QUATRE))
                .isInstanceOfSatisfying(QuotaDepasseException.class,
                        e -> assertThat(e.attente()).hasValueSatisfying(d -> assertThat(d).isPositive()));

        api.verify(1, postRequestedFor(urlEqualTo("/siren")));
    }

    @Test
    void neRelancePasUneRequeteRefusee() {
        api.stubFor(post("/siren").willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> client(reglages("", 1000, 3, Duration.ofSeconds(5))).unitesLegales(QUATRE))
                .isInstanceOf(AppelRefuseException.class);
        api.verify(1, postRequestedFor(urlEqualTo("/siren")));
    }

    @Test
    void metEnCacheLIdentiteLueALUnite() {
        api.stubFor(get("/siren/552032534").willReturn(okJson(Fixtures.lire("sirene/unite-legale-552032534.json"))));
        SireneClient client = client(reglages("", 1000, 2, Duration.ofSeconds(5)));

        assertThat(client.uniteLegale(new Siren("552032534"))).hasValueSatisfying(
                u -> assertThat(u.denomination()).contains("DANONE"));
        assertThat(client.uniteLegale(new Siren("552032534"))).isPresent();

        api.verify(1, getRequestedFor(urlEqualTo("/siren/552032534")));
    }

    @Test
    void renvoieVidePourUnSirenInconnu() {
        api.stubFor(get("/siren/130005481").willReturn(aResponse().withStatus(404)));

        assertThat(client(reglages("", 1000, 2, Duration.ofSeconds(5))).uniteLegale(new Siren("130005481"))).isEmpty();
    }

    @Test
    void neDemandeJamaisPlusDeMilleSirenParRequete() {
        api.stubFor(post("/siren").willReturn(okJson(Fixtures.lire("sirene/aucun-resultat.json")).withStatus(404)));
        List<Siren> milleEtUn = IntStream.range(0, 1001)
                .mapToObj(i -> Siren.lire(String.format("%08d", i + 10_000_000)
                        + cleDeLuhn(String.format("%08d", i + 10_000_000))).orElseThrow())
                .toList();

        client(reglages("", 5000, 2, Duration.ofSeconds(5))).unitesLegales(milleEtUn);

        api.verify(2, postRequestedFor(urlEqualTo("/siren")));
    }

    private static String cleDeLuhn(String huitChiffres) {
        for (int cle = 0; cle < 10; cle++) {
            if (Siren.lire(huitChiffres + cle).isPresent()) {
                return String.valueOf(cle);
            }
        }
        throw new IllegalStateException();
    }

    private SireneClient client(SireneProperties reglages) {
        return new SireneClient(reglages);
    }

    private static SireneProperties reglages(String cle, int tailleLot, int tentatives, Duration delai) {
        return new SireneProperties(URI.create(api.baseUrl()), cle, delai, 10_000, tailleLot, Duration.ofHours(24),
                1_000, Fixtures.protectionRapide(tentatives));
    }
}
