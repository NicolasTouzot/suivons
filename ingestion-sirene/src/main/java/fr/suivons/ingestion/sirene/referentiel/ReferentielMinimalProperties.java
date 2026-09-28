package fr.suivons.ingestion.sirene.referentiel;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages du rafraîchissement du référentiel minimal (SPEC.md §6.3).
 *
 * @param delaiGrace une entreprise sans flux n'est retirée que si elle n'a pas été rafraîchie depuis ce délai :
 *                   protège une entreprise ajoutée par un rattachement dont les flux ne sont pas encore écrits
 * @param tailleLot  SIREN par lot (deux appels Sirene et une transaction par lot ; maximum INSEE : 1 000)
 */
@ConfigurationProperties("suivons.referentiel-minimal")
public record ReferentielMinimalProperties(
        @DefaultValue("1d") Duration delaiGrace,
        @DefaultValue("1000") int tailleLot) {
}
