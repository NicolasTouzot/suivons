package fr.suivons.ingestion.core.pipeline;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Téléchargement des jeux de données dans le cache local de la source : écriture dans un fichier temporaire,
 * puis remplacement atomique, pour ne jamais laisser un fichier partiel en cache.
 */
public class Telechargeur {

    private final HttpClient http;
    private final Duration delaiMaximal;
    private final String userAgent;

    public Telechargeur(Duration delaiMaximal, String userAgent) {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(ProxySelector.getDefault())
                .build();
        this.delaiMaximal = delaiMaximal;
        this.userAgent = userAgent;
    }

    public Path telecharger(URI url, Path cible) throws IOException {
        Files.createDirectories(cible.toAbsolutePath().getParent());
        Path temporaire = Files.createTempFile(cible.toAbsolutePath().getParent(), cible.getFileName().toString(), ".part");
        try {
            HttpResponse<Path> reponse = http.send(HttpRequest.newBuilder(url)
                            .timeout(delaiMaximal)
                            .header("User-Agent", userAgent)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofFile(temporaire));
            if (reponse.statusCode() != 200) {
                throw new IOException("Téléchargement de " + url + " : HTTP " + reponse.statusCode());
            }
            return Files.move(temporaire, cible, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Téléchargement de " + url + " interrompu", e);
        } finally {
            Files.deleteIfExists(temporaire);
        }
    }
}
