package fr.suivons.ingestion.core.source;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * Point d'extension d'une source de flux (SPEC.md §7.3). Un module `ingestion-<source>` en fournit une
 * implémentation ; ingestion-core se charge du reste : suivi du run, checksum, données brutes, rejets,
 * rattachement, écriture idempotente dans core.flux, rafraîchissement, compte rendu.
 */
public interface SourceFlux {

    /** Code de la source dans ops.source (ex. `DECP`). */
    String code();

    /**
     * Table des données brutes de la source (ex. `raw.decp_record`), créée par la migration de la source avec
     * les colonnes `source_record_id` (clé primaire), `run_id`, `payload` (jsonb), `checksum`, `recu_le`.
     */
    String tableBrute();

    /** Télécharge (ou retrouve en cache) la version courante du jeu de données. */
    FichierSource extraire(ContexteExtraction contexte) throws IOException;

    /**
     * Enregistrements du fichier, un par `sourceRecordId` (la source dédoublonne). Le flux est fermé par le pipeline.
     */
    Stream<EnregistrementSource> lire(FichierSource fichier) throws IOException;

    /** Transformation d'un enregistrement brut en flux normalisé, ou rejet motivé (jamais d'exception attendue). */
    Transformation transformer(EnregistrementSource enregistrement);
}
