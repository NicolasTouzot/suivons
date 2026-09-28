package fr.suivons.ingestion.core.pipeline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

class TelechargeurIT {

    @RegisterExtension
    static WireMockExtension serveur = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @TempDir
    Path cache;

    private final Telechargeur telechargeur = new Telechargeur(Duration.ofSeconds(10), "suivons-test");

    @Test
    void telechargeLeFichierDansLeCacheAvecUnUserAgentDescriptif() throws IOException {
        serveur.stubFor(get("/jeu.csv").willReturn(aResponse().withBody("a;b\n1;2\n")));

        Path fichier = telechargeur.telecharger(URI.create(serveur.baseUrl() + "/jeu.csv"), cache.resolve("decp/jeu.csv"));

        assertThat(fichier).hasContent("a;b\n1;2");
        assertThat(Checksums.sha256(fichier)).isEqualTo(Checksums.sha256("a;b\n1;2\n"));
        serveur.verify(getRequestedFor(urlEqualTo("/jeu.csv")).withHeader("User-Agent", equalTo("suivons-test")));
    }

    @Test
    void neLaissePasDeFichierPartielEnCasDErreur() throws IOException {
        Path cible = cache.resolve("jeu.csv");
        Files.writeString(cible, "version précédente");
        serveur.stubFor(get("/jeu.csv").willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> telechargeur.telecharger(URI.create(serveur.baseUrl() + "/jeu.csv"), cible))
                .isInstanceOf(IOException.class).hasMessageContaining("503");

        assertThat(cible).hasContent("version précédente");
        try (var fichiers = Files.list(cache)) {
            assertThat(fichiers).containsExactly(cible);
        }
    }
}
