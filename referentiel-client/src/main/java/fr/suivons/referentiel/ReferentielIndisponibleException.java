package fr.suivons.referentiel;

/**
 * Une API d'appui ne répond pas utilement (indisponible, trop lente, disjoncteur ouvert).
 * Les appelants dégradent l'identité ou le repli de recherche, jamais les montants (SPEC.md §8).
 */
public class ReferentielIndisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReferentielIndisponibleException(String message) {
        super(message);
    }

    public ReferentielIndisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
