package fr.suivons.ingestion.core.pipeline;

/** Échec d'une ingestion : le run est marqué ECHEC et le mart n'est pas rafraîchi (SPEC.md §7.3). */
public class IngestionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
