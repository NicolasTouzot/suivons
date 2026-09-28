package fr.suivons.referentiel;

import java.time.Duration;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages de protection d'un client d'API d'appui (SPEC.md §8 : timeout court, disjoncteur, quotas).
 *
 * @param tentatives           nombre total de tentatives d'un appel (1 = pas de relance)
 * @param attenteInitiale      attente avant la première relance, doublée ensuite
 * @param attenteMaximale      attente maximale acceptée entre deux tentatives (Retry-After compris)
 * @param seuilEchecPourcent   taux d'échec qui ouvre le disjoncteur
 * @param appelsMinimum        nombre d'appels observés avant de juger le taux d'échec
 * @param dureeOuverture       durée pendant laquelle le disjoncteur ouvert refuse les appels
 * @param attentePermis        attente maximale d'un permis du limiteur de débit
 */
public record ProtectionProperties(
        @DefaultValue("3") int tentatives,
        @DefaultValue("1s") Duration attenteInitiale,
        @DefaultValue("60s") Duration attenteMaximale,
        @DefaultValue("50") int seuilEchecPourcent,
        @DefaultValue("5") int appelsMinimum,
        @DefaultValue("60s") Duration dureeOuverture,
        @DefaultValue("70s") Duration attentePermis) {
}
