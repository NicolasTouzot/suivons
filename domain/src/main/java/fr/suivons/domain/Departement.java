package fr.suivons.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/** Département déduit du code officiel géographique (code commune INSEE) : 2 caractères, 3 en outre-mer. */
public final class Departement {

    private static final Pattern CODE_COMMUNE = Pattern.compile("(\\d{2}|2A|2B)\\d{3}");

    private Departement() {
    }

    /** Code du département de la commune ; vide si le code commune est absent, masqué ou mal formé. */
    public static Optional<String> depuisCodeCommune(String codeCommune) {
        if (codeCommune == null || !CODE_COMMUNE.matcher(codeCommune).matches()) {
            return Optional.empty();
        }
        return Optional.of(codeCommune.startsWith("97") || codeCommune.startsWith("98")
                ? codeCommune.substring(0, 3)
                : codeCommune.substring(0, 2));
    }
}
