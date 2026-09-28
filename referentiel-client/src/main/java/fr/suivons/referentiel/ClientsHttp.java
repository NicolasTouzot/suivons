package fr.suivons.referentiel;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** Fabrique des clients HTTP des API d'appui : timeout court, proxy système respecté. */
public final class ClientsHttp {

    private ClientsHttp() {
    }

    public static RestClient.Builder client(URI url, Duration delaiMaximal) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(delaiMaximal)
                .proxy(ProxySelector.getDefault())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(http);
        fabrique.setReadTimeout(delaiMaximal);
        return RestClient.builder().baseUrl(url.toString()).requestFactory(fabrique);
    }

    /** Délai indiqué par l'en-tête Retry-After (en secondes), s'il est présent et lisible. */
    public static Optional<Duration> retryAfter(HttpHeaders entetes) {
        String valeur = entetes.getFirst(HttpHeaders.RETRY_AFTER);
        if (valeur == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.ofSeconds(Long.parseLong(valeur.trim())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Traduit une erreur réseau (délai dépassé, connexion refusée) en indisponibilité de l'API. */
    public static ReferentielIndisponibleException indisponible(String api, ResourceAccessException erreur) {
        String motif = erreur.getCause() instanceof HttpTimeoutException ? "délai dépassé" : "erreur réseau";
        return new ReferentielIndisponibleException(api + " : " + motif, erreur);
    }
}
