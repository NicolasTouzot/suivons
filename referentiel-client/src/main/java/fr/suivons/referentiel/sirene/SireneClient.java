package fr.suivons.referentiel.sirene;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import fr.suivons.domain.Siren;
import fr.suivons.referentiel.AppelRefuseException;
import fr.suivons.referentiel.ClientsHttp;
import fr.suivons.referentiel.Protection;
import fr.suivons.referentiel.QuotaDepasseException;
import fr.suivons.referentiel.ReferentielIndisponibleException;

/**
 * Client de l'API Sirene 3.11 (INSEE). Lectures groupées pour le rafraîchissement du référentiel,
 * lecture unitaire mise en cache pour la fiche entreprise. N'écrit jamais en base (ADR 0004).
 */
public class SireneClient {

    static final String API = "API Sirene";
    static final String ENTETE_CLE = "X-INSEE-Api-Key-Integration";
    static final String NON_DIFFUSE = "[ND]";
    private static final String CHAMPS_UNITES = String.join(",",
            "siren", "statutDiffusionUniteLegale", "denominationUniteLegale", "etatAdministratifUniteLegale",
            "activitePrincipaleUniteLegale", "nomenclatureActivitePrincipaleUniteLegale",
            "categorieJuridiqueUniteLegale", "categorieEntreprise");
    private static final String CHAMPS_SIEGES =
            "siren,siret,codeCommuneEtablissement,libelleCommuneEtablissement";
    private static final DateTimeFormatter DATE_SIRENE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final RestClient http;
    private final Protection protection;
    private final int tailleLot;
    private final Cache<Siren, Optional<UniteLegale>> cache;
    private final Clock horloge;
    private volatile Instant quotaEpuiseJusqua = Instant.MIN;

    public SireneClient(SireneProperties reglages) {
        this(reglages, Clock.systemUTC());
    }

    SireneClient(SireneProperties reglages, Clock horloge) {
        RestClient.Builder builder = ClientsHttp.client(reglages.url(), reglages.delaiMaximal());
        if (!reglages.cleApi().isBlank()) {
            builder.defaultHeader(ENTETE_CLE, reglages.cleApi());
        }
        this.http = builder.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
        this.protection = new Protection(API, reglages.requetesParMinute(), Duration.ofMinutes(1),
                reglages.protection());
        this.tailleLot = Math.min(reglages.tailleLot(), 1000);
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(reglages.dureeCache())
                .maximumSize(reglages.tailleCache())
                .build();
        this.horloge = horloge;
    }

    /** Unités légales des SIREN demandés (requêtes groupées) ; les SIREN inconnus de l'INSEE sont absents. */
    public List<UniteLegale> unitesLegales(Collection<Siren> sirens) {
        List<UniteLegale> resultat = new ArrayList<>();
        for (List<Siren> lot : lots(sirens)) {
            ReponsesSirene.UnitesLegales reponse = post("/siren", requete(ouSiren(lot), CHAMPS_UNITES, null),
                    ReponsesSirene.UnitesLegales.class, ReponsesSirene.UnitesLegales.AUCUNE);
            reponse.unitesLegales().stream().map(SireneClient::versUniteLegale).forEach(resultat::add);
        }
        return resultat;
    }

    /** Établissements sièges des SIREN demandés (requêtes groupées). */
    public List<Siege> sieges(Collection<Siren> sirens) {
        List<Siege> resultat = new ArrayList<>();
        for (List<Siren> lot : lots(sirens)) {
            String q = "(" + ouSiren(lot) + ") AND etablissementSiege:true";
            ReponsesSirene.Etablissements reponse = post("/siret", requete(q, CHAMPS_SIEGES, null),
                    ReponsesSirene.Etablissements.class, ReponsesSirene.Etablissements.AUCUN);
            reponse.etablissements().stream().map(SireneClient::versSiege).forEach(resultat::add);
        }
        return resultat;
    }

    /** Identité d'une unité légale, lue à la demande et conservée en cache (fiche entreprise, SPEC §5). */
    public Optional<UniteLegale> uniteLegale(Siren siren) {
        Optional<UniteLegale> enCache = cache.getIfPresent(siren);
        if (enCache != null) {
            return enCache;
        }
        Optional<UniteLegale> lue = appeler(() -> http.get()
                .uri("/siren/{siren}", siren.valeur())
                .exchange((requete, reponse) -> {
                    noterQuota(reponse.getHeaders());
                    if (reponse.getStatusCode().value() == 404) {
                        return Optional.<UniteLegale>empty();
                    }
                    verifier(reponse.getStatusCode(), reponse.getHeaders());
                    ReponsesSirene.UneUniteLegale corps = reponse.bodyTo(ReponsesSirene.UneUniteLegale.class);
                    return Optional.ofNullable(corps).map(c -> versUniteLegale(c.uniteLegale()));
                }));
        cache.put(siren, lue);
        return lue;
    }

    /**
     * Unités légales modifiées par l'INSEE depuis une date (rafraîchissement incrémental), page par page :
     * le consommateur reçoit chaque page au fil du parcours par curseur.
     */
    public void unitesLegalesModifieesDepuis(LocalDateTime depuis, Consumer<List<UniteLegale>> page) {
        String q = "dateDernierTraitementUniteLegale:[" + DATE_SIRENE.format(depuis.truncatedTo(ChronoUnit.SECONDS))
                + " TO *]";
        String curseur = "*";
        while (true) {
            ReponsesSirene.UnitesLegales reponse = post("/siren", requete(q, CHAMPS_UNITES, curseur),
                    ReponsesSirene.UnitesLegales.class, ReponsesSirene.UnitesLegales.AUCUNE);
            List<UniteLegale> unites = reponse.unitesLegales().stream().map(SireneClient::versUniteLegale).toList();
            if (!unites.isEmpty()) {
                page.accept(unites);
            }
            String suivant = reponse.header() == null ? null : reponse.header().curseurSuivant();
            if (unites.isEmpty() || suivant == null || suivant.equals(curseur)) {
                return;
            }
            curseur = suivant;
        }
    }

    private <T> T post(String chemin, MultiValueMap<String, String> formulaire, Class<T> type, T siAucun) {
        return appeler(() -> http.post()
                .uri(chemin)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formulaire)
                .exchange((requete, reponse) -> {
                    noterQuota(reponse.getHeaders());
                    if (reponse.getStatusCode().value() == 404) {
                        return siAucun;
                    }
                    verifier(reponse.getStatusCode(), reponse.getHeaders());
                    T corps = reponse.bodyTo(type);
                    return corps == null ? siAucun : corps;
                }));
    }

    private <T> T appeler(java.util.function.Supplier<T> appel) {
        Instant maintenant = horloge.instant();
        if (maintenant.isBefore(quotaEpuiseJusqua)) {
            throw new QuotaDepasseException(API + " : quota horaire épuisé",
                    Duration.between(maintenant, quotaEpuiseJusqua));
        }
        return protection.executer(() -> {
            try {
                return appel.get();
            } catch (ResourceAccessException e) {
                throw ClientsHttp.indisponible(API, e);
            }
        });
    }

    /** Quota horaire lu dans les en-têtes : s'il est épuisé, les appels échouent vite jusqu'à sa remise à zéro. */
    private void noterQuota(HttpHeaders entetes) {
        String restant = entetes.getFirst("x-quota-remaining");
        String remiseAZero = entetes.getFirst("x-quota-reset");
        if ("0".equals(restant) && remiseAZero != null) {
            try {
                quotaEpuiseJusqua = Instant.ofEpochMilli(Long.parseLong(remiseAZero));
            } catch (NumberFormatException e) {
                quotaEpuiseJusqua = horloge.instant().plus(Duration.ofHours(1));
            }
        }
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

    private List<List<Siren>> lots(Collection<Siren> sirens) {
        List<Siren> distincts = sirens.stream().distinct().toList();
        List<List<Siren>> lots = new ArrayList<>();
        for (int debut = 0; debut < distincts.size(); debut += tailleLot) {
            lots.add(distincts.subList(debut, Math.min(debut + tailleLot, distincts.size())));
        }
        return lots;
    }

    private static String ouSiren(List<Siren> lot) {
        return lot.stream().map(s -> "siren:" + s.valeur()).collect(Collectors.joining(" OR "));
    }

    private MultiValueMap<String, String> requete(String q, String champs, String curseur) {
        MultiValueMap<String, String> formulaire = new LinkedMultiValueMap<>();
        formulaire.add("q", q);
        formulaire.add("champs", champs);
        formulaire.add("nombre", String.valueOf(tailleLot));
        if (curseur != null) {
            formulaire.add("curseur", curseur);
        }
        return formulaire;
    }

    static UniteLegale versUniteLegale(ReponsesSirene.UniteLegaleJson json) {
        ReponsesSirene.PeriodeJson periode = json.periodesUniteLegale() == null ? null
                : json.periodesUniteLegale().stream()
                        .filter(p -> p.dateFin() == null)
                        .findFirst()
                        .orElse(json.periodesUniteLegale().isEmpty() ? null : json.periodesUniteLegale().getFirst());
        Optional<ActivitePrincipale> activite = periode == null ? Optional.empty()
                : valeur(periode.activitePrincipaleUniteLegale()).flatMap(code ->
                        valeur(periode.nomenclatureActivitePrincipaleUniteLegale())
                                .map(nomenclature -> new ActivitePrincipale(code, nomenclature)));
        return new UniteLegale(
                new Siren(json.siren()),
                !"P".equals(json.statutDiffusionUniteLegale()),
                periode == null ? Optional.empty() : valeur(periode.denominationUniteLegale()),
                periode == null ? Optional.empty() : valeur(periode.categorieJuridiqueUniteLegale()),
                activite,
                periode == null || !"C".equals(periode.etatAdministratifUniteLegale()),
                valeur(json.categorieEntreprise()));
    }

    static Siege versSiege(ReponsesSirene.EtablissementJson json) {
        ReponsesSirene.AdresseJson adresse = json.adresseEtablissement();
        return new Siege(
                new Siren(json.siren()),
                json.siret(),
                adresse == null ? Optional.empty() : valeur(adresse.codeCommuneEtablissement()),
                adresse == null ? Optional.empty() : valeur(adresse.libelleCommuneEtablissement()));
    }

    /** Valeur exploitable : ni absente, ni vide, ni masquée par l'INSEE. */
    static Optional<String> valeur(String brute) {
        if (brute == null || brute.isBlank() || NON_DIFFUSE.equals(brute)) {
            return Optional.empty();
        }
        return Optional.of(brute);
    }

    /** État du disjoncteur (supervision). */
    public String etatDisjoncteur() {
        return protection.etatDisjoncteur().name();
    }
}
