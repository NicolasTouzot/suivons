package fr.suivons.ingestion.core.pipeline;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import fr.suivons.ingestion.core.source.FichierSource;

/** État partagé par les étapes d'un run : identifiant, fichier traité, compteurs. */
final class EtatRun {

    final OffsetDateTime extraitLe = OffsetDateTime.now();
    final AtomicInteger lus = new AtomicInteger();
    final AtomicInteger charges = new AtomicInteger();
    final AtomicInteger rejetes = new AtomicInteger();
    volatile long runId;
    volatile FichierSource fichier;
    volatile boolean rienAFaire;

    SuiviRuns.Compteurs compteurs() {
        return new SuiviRuns.Compteurs(lus.get(), charges.get(), rejetes.get());
    }
}
