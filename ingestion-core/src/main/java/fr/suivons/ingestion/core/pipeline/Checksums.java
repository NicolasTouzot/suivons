package fr.suivons.ingestion.core.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Empreintes SHA-256 des fichiers et des enregistrements reçus. */
public final class Checksums {

    private Checksums() {
    }

    public static String sha256(Path fichier) throws IOException {
        MessageDigest empreinte = algorithme();
        try (InputStream flux = Files.newInputStream(fichier)) {
            byte[] tampon = new byte[64 * 1024];
            int lus;
            while ((lus = flux.read(tampon)) != -1) {
                empreinte.update(tampon, 0, lus);
            }
        }
        return HexFormat.of().formatHex(empreinte.digest());
    }

    public static String sha256(String contenu) {
        return HexFormat.of().formatHex(algorithme().digest(contenu.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest algorithme() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
