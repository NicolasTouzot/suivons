package fr.suivons.ingestion.sirene;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Accès aux fichiers de /fixtures (classpath des TI). */
public final class Fixtures {

    private Fixtures() {
    }

    public static byte[] octets(String chemin) {
        try (InputStream flux = Fixtures.class.getResourceAsStream("/" + chemin)) {
            if (flux == null) {
                throw new IllegalArgumentException("Fixture introuvable : " + chemin);
            }
            return flux.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String lire(String chemin) {
        return new String(octets(chemin), StandardCharsets.UTF_8);
    }
}
