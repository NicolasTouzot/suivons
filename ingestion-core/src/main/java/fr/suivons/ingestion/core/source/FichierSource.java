package fr.suivons.ingestion.core.source;

import java.nio.file.Path;

/**
 * Version d'un jeu de données prête à être lue.
 *
 * @param chemin        fichier local (téléchargé ou en cache)
 * @param versionSource date ou identifiant de version publié par le producteur, s'il existe (sinon le checksum)
 */
public record FichierSource(Path chemin, String versionSource) {
}
