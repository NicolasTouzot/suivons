package fr.suivons.referentiel.recherche;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import fr.suivons.domain.Siren;
import fr.suivons.referentiel.Fixtures;

class RechercheEntreprisesClientIT {

    @RegisterExtension
    static WireMockExtension api = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @Test
    void renvoieLesCandidatsDansLOrdreDeLApi() {
        api.stubFor(get(urlPathEqualTo("/search"))
                .willReturn(okJson(Fixtures.lire("recherche-entreprises/recherche-danone.json"))));

        List<CandidatEntreprise> candidats = client(2).rechercher("danone paris");

        assertThat(candidats).extracting(CandidatEntreprise::siren)
                .containsExactly(new Siren("552032534"), new Siren("412565228"));
        CandidatEntreprise danone = candidats.getFirst();
        assertThat(danone.nom()).isEqualTo("DANONE");
        assertThat(danone.active()).isTrue();
        assertThat(danone.communeSiege()).contains("PARIS 9");
        assertThat(danone.codeCommuneSiege()).contains("75109");
        assertThat(danone.score()).contains(0.97);
        assertThat(candidats.get(1).active()).isFalse();

        api.verify(getRequestedFor(urlPathEqualTo("/search"))
                .withQueryParam("q", equalTo("danone paris"))
                .withQueryParam("per_page", equalTo("10"))
                .withQueryParam("minimal", equalTo("true"))
                .withQueryParam("include", equalTo("siege,score"))
                .withHeader("User-Agent", equalTo("suivons-test")));
    }

    @Test
    void relanceApresUn429() {
        api.stubFor(get(urlPathEqualTo("/search")).inScenario("quota").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0"))
                .willSetStateTo("libre"));
        api.stubFor(get(urlPathEqualTo("/search")).inScenario("quota").whenScenarioStateIs("libre")
                .willReturn(okJson(Fixtures.lire("recherche-entreprises/recherche-danone.json"))));

        assertThat(client(3).rechercher("danone")).hasSize(2);
        api.verify(2, getRequestedFor(urlPathEqualTo("/search")));
    }

    @Test
    void nAppellePasLApiPourUnTexteVide() {
        assertThat(client(2).rechercher("  ")).isEmpty();
        api.verify(0, getRequestedFor(urlPathEqualTo("/search")));
    }

    private static RechercheEntreprisesClient client(int tentatives) {
        return new RechercheEntreprisesClient(new RechercheEntreprisesProperties(URI.create(api.baseUrl()),
                "suivons-test", Duration.ofSeconds(5), 100, 10, Fixtures.protectionRapide(tentatives)));
    }
}
