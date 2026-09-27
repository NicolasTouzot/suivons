package fr.suivons.referentiel.recherche;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import fr.suivons.domain.Siren;
import fr.suivons.referentiel.AppelRefuseException;
import fr.suivons.referentiel.ClientsHttp;
import fr.suivons.referentiel.Protection;
import fr.suivons.referentiel.QuotaDepasseException;
import fr.suivons.referentiel.ReferentielIndisponibleException;

/**
 * Client de l'API Recherche d'entreprises : candidats par nom pour le rattachement (SPEC §6.5)
 * et repli de la recherche. Les entreprises non diffusibles n'y figurent pas.
 */
public class RechercheEntreprisesClient {

    static final String API = "API Recherche d'entreprises";

    private final RestClient http;
    private final Protection protection;
    private final int candidatsMaximum;

    public RechercheEntreprisesClient(RechercheEntreprisesProperties reglages) {
        this.http = ClientsHttp.client(reglages.url(), reglages.delaiMaximal())
                .defaultHeader(HttpHeaders.USER_AGENT, reglages.userAgent())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.protection = new Protection(API, reglages.requetesParSeconde(), Duration.ofSeconds(1),
                reglages.protection());
        this.candidatsMaximum = Math.min(reglages.candidatsMaximum(), 25);
    }

    /** Candidats pour un texte (nom, éventuellement adresse), dans l'ordre de pertinence de l'API. */
    public List<CandidatEntreprise> rechercher(String texte) {
        if (texte == null || texte.isBlank()) {
            return List.of();
        }
        ReponsesRecherche.Resultats resultats = protection.executer(() -> {
            try {
                return http.get()
                        .uri(uri -> uri.path("/search")
                                .queryParam("q", texte)
                                .queryParam("per_page", candidatsMaximum)
                                .queryParam("minimal", true)
                                .queryParam("include", "siege,score")
                                .build())
                        .exchange((requete, reponse) -> {
                            verifier(reponse.getStatusCode(), reponse.getHeaders());
                            return reponse.bodyTo(ReponsesRecherche.Resultats.class);
                        });
            } catch (ResourceAccessException e) {
                throw ClientsHttp.indisponible(API, e);
            }
        });
        if (resultats == null || resultats.results() == null) {
            return List.of();
        }
        return resultats.results().stream()
                .map(RechercheEntreprisesClient::versCandidat)
                .flatMap(Optional::stream)
                .toList();
    }

    private static Optional<CandidatEntreprise> versCandidat(ReponsesRecherche.Resultat resultat) {
        return Siren.lire(resultat.siren()).map(siren -> {
            ReponsesRecherche.SiegeJson siege = resultat.siege();
            return new CandidatEntreprise(
                    siren,
                    Objects.requireNonNullElse(resultat.nomComplet(), ""),
                    !"C".equals(resultat.etatAdministratif()),
                    valeur(resultat.natureJuridique()),
                    siege == null ? Optional.empty() : valeur(siege.commune()),
                    siege == null ? Optional.empty() : valeur(siege.libelleCommune()),
                    siege == null ? Optional.empty() : valeur(siege.codePostal()),
                    Optional.ofNullable(resultat.score()));
        });
    }

    private static void verifier(HttpStatusCode statut, HttpHeaders entetes) {
        if (statut.is2xxSuccessful()) {
            return;
        }
        if (statut.value() == 429) {
            throw new QuotaDepasseException(API + " : trop de requêtes (429)",
                    ClientsHttp.retryAfter(entetes).orElse(null));
        }
        if (statut.is5xxServerError()) {
            throw new ReferentielIndisponibleException(API + " : erreur " + statut.value());
        }
        throw new AppelRefuseException(API + " : requête refusée (" + statut.value() + ")");
    }

    private static Optional<String> valeur(String brute) {
        return brute == null || brute.isBlank() || "[ND]".equals(brute) ? Optional.empty() : Optional.of(brute);
    }

    /** État du disjoncteur (supervision). */
    public String etatDisjoncteur() {
        return protection.etatDisjoncteur().name();
    }
}
