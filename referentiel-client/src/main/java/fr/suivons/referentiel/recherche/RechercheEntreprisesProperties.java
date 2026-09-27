package fr.suivons.referentiel.recherche;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import fr.suivons.referentiel.ProtectionProperties;

/**
 * Configuration du client de l'API Recherche d'entreprises (DINUM, docs/sources/recherche-entreprises.md).
 *
 * @param url                base de l'API
 * @param userAgent          en-tête User-Agent descriptif, recommandé par l'API
 * @param delaiMaximal       timeout de connexion et de lecture
 * @param requetesParSeconde débit maximal côté client (limite de l'API : 7 par seconde et par IP)
 * @param candidatsMaximum   résultats demandés par recherche (maximum de l'API : 25)
 */
@ConfigurationProperties("suivons.referentiel.recherche-entreprises")
public record RechercheEntreprisesProperties(
        @DefaultValue("https://recherche-entreprises.api.gouv.fr") URI url,
        @DefaultValue("suivons/0.1 (+https://github.com/NicolasTouzot/suivons)") String userAgent,
        @DefaultValue("5s") Duration delaiMaximal,
        @DefaultValue("5") int requetesParSeconde,
        @DefaultValue("10") int candidatsMaximum,
        @DefaultValue ProtectionProperties protection) {
}
