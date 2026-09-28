package fr.suivons.referentiel;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Accès aux fichiers de /fixtures et réglages de protection rapides pour les TI. */
public final class Fixtures {

    private Fixtures() {
    }

    public static String lire(String chemin) {
        try (InputStream flux = Fixtures.class.getResourceAsStream("/" + chemin)) {
            if (flux == null) {
                throw new IllegalArgumentException("Fixture introuvable : " + chemin);
            }
            return new String(flux.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Relances rapides, disjoncteur jugé dès 2 appels : les TI restent courts. */
    public static ProtectionProperties protectionRapide(int tentatives) {
        return new ProtectionProperties(tentatives, Duration.ofMillis(10), Duration.ofSeconds(2), 50, 2,
                Duration.ofMinutes(1), Duration.ofSeconds(1));
    }
}
