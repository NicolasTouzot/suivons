package fr.suivons.domain;

/** Flux qui viole une règle métier (bornes, provenance) : il est rejeté, jamais chargé. */
public class FluxInvalideException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public FluxInvalideException(String message) {
        super(message);
    }
}
