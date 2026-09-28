package fr.suivons.referentiel;

import java.time.Duration;
import java.util.Optional;

/** Quota d'une API d'appui atteint (réponse 429, ou quota horaire épuisé d'après les en-têtes). */
public class QuotaDepasseException extends ReferentielIndisponibleException {

    private static final long serialVersionUID = 1L;

    private final transient Duration attente;

    public QuotaDepasseException(String message, Duration attente) {
        super(message);
        this.attente = attente;
    }

    /** Délai à respecter avant un nouvel appel, s'il est connu (en-tête Retry-After ou reset du quota). */
    public Optional<Duration> attente() {
        return Optional.ofNullable(attente);
    }
}
