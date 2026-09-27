package fr.suivons.domain;

/** Identifiant (SIREN, SIRET, code commune…) qui ne respecte pas son format officiel. */
public class IdentifiantInvalideException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public IdentifiantInvalideException(String message) {
        super(message);
    }
}
