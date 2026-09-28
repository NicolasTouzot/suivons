package fr.suivons.ingestion.core.pipeline;

import static fr.suivons.db.jooq.ops.Tables.INGESTION_RUN;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.jooq.DSLContext;

/** Suivi des exécutions dans ops.ingestion_run (SPEC.md §6.1), affiché sur la page « état des sources ». */
public class SuiviRuns {

    public enum Statut { EN_COURS, SUCCES, ECHEC }

    private final DSLContext dsl;

    public SuiviRuns(DSLContext dsl) {
        this.dsl = dsl;
    }

    public long demarrer(String sourceCode) {
        return dsl.insertInto(INGESTION_RUN)
                .set(INGESTION_RUN.SOURCE_CODE, sourceCode)
                .returning(INGESTION_RUN.ID)
                .fetchSingle(INGESTION_RUN.ID);
    }

    /** Checksum du fichier traité par le dernier run réussi de la source. */
    public Optional<String> dernierChecksumReussi(String sourceCode) {
        return dsl.select(INGESTION_RUN.CHECKSUM_FICHIER)
                .from(INGESTION_RUN)
                .where(INGESTION_RUN.SOURCE_CODE.eq(sourceCode))
                .and(INGESTION_RUN.STATUT.eq(Statut.SUCCES.name()))
                .and(INGESTION_RUN.CHECKSUM_FICHIER.isNotNull())
                .orderBy(INGESTION_RUN.DEBUT.desc(), INGESTION_RUN.ID.desc())
                .limit(1)
                .fetchOptional(INGESTION_RUN.CHECKSUM_FICHIER);
    }

    public void noterFichier(long runId, String checksum, String versionSource) {
        dsl.update(INGESTION_RUN)
                .set(INGESTION_RUN.CHECKSUM_FICHIER, checksum)
                .set(INGESTION_RUN.VERSION_SOURCE, versionSource)
                .where(INGESTION_RUN.ID.eq(runId))
                .execute();
    }

    public void terminer(long runId, Statut statut, Compteurs compteurs) {
        dsl.update(INGESTION_RUN)
                .set(INGESTION_RUN.STATUT, statut.name())
                .set(INGESTION_RUN.FIN, OffsetDateTime.now())
                .set(INGESTION_RUN.LUS, compteurs.lus())
                .set(INGESTION_RUN.CHARGES, compteurs.charges())
                .set(INGESTION_RUN.REJETES, compteurs.rejetes())
                .where(INGESTION_RUN.ID.eq(runId))
                .execute();
    }

    /** Compteurs d'un run : enregistrements lus, flux créés ou modifiés, enregistrements rejetés. */
    public record Compteurs(int lus, int charges, int rejetes) {
    }
}
