package fr.suivons.ingestion.core;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages communs des ingestions.
 *
 * @param repertoireCache       cache des fichiers téléchargés (un sous-répertoire par source, jamais versionné)
 * @param tailleLot             enregistrements par transaction (chunk Spring Batch)
 * @param delaiTelechargement   durée maximale d'un téléchargement
 * @param userAgent             en-tête User-Agent des téléchargements
 */
@ConfigurationProperties("suivons.ingestion")
public record IngestionProperties(
        @DefaultValue("data/cache") Path repertoireCache,
        @DefaultValue("1000") int tailleLot,
        @DefaultValue("30m") Duration delaiTelechargement,
        @DefaultValue("suivons/0.1 (+https://github.com/NicolasTouzot/suivons)") String userAgent) {
}
