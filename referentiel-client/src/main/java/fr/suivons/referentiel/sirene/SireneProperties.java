package fr.suivons.referentiel.sirene;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import fr.suivons.referentiel.ProtectionProperties;

/**
 * Configuration du client de l'API Sirene 3.11 (SPEC.md §4, docs/sources/sirene.md).
 *
 * @param url               base de l'API
 * @param cleApi            clé du plan « Accès public » ; vide : aucun en-tête envoyé (clé injectée ailleurs)
 * @param delaiMaximal      timeout de connexion et de lecture
 * @param requetesParMinute débit maximal côté client (quota INSEE : 30 par minute)
 * @param tailleLot         SIREN par requête groupée (maximum INSEE : 1 000)
 * @param dureeCache        durée de conservation de l'identité lue à l'unité (fiche entreprise)
 * @param tailleCache       nombre maximal d'identités en cache
 */
@ConfigurationProperties("suivons.referentiel.sirene")
public record SireneProperties(
        @DefaultValue("https://api.insee.fr/api-sirene/3.11") URI url,
        @DefaultValue("") String cleApi,
        @DefaultValue("10s") Duration delaiMaximal,
        @DefaultValue("30") int requetesParMinute,
        @DefaultValue("1000") int tailleLot,
        @DefaultValue("24h") Duration dureeCache,
        @DefaultValue("10000") long tailleCache,
        @DefaultValue ProtectionProperties protection) {
}
