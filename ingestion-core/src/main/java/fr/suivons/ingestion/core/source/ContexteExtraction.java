package fr.suivons.ingestion.core.source;

import java.nio.file.Path;

/**
 * Contexte fourni à l'extraction.
 *
 * @param runId           exécution en cours (ops.ingestion_run)
 * @param repertoireCache répertoire de cache des fichiers téléchargés, propre à la source
 */
public record ContexteExtraction(long runId, Path repertoireCache) {
}
