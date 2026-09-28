package fr.suivons.ingestion.core.pipeline;

import static fr.suivons.db.jooq.ops.Tables.REJET;

import java.util.List;

import org.jooq.DSLContext;
import org.jooq.JSONB;

/** Enregistrements rejetés à la transformation, avec leur motif et leur contenu (ops.rejet). */
public class DepotRejets {

    private final DSLContext dsl;

    public DepotRejets(DSLContext dsl) {
        this.dsl = dsl;
    }

    public record RejetMotive(String sourceRecordId, String motif, String payload) {
    }

    public int enregistrer(long runId, List<RejetMotive> rejets) {
        if (rejets.isEmpty()) {
            return 0;
        }
        var insertion = dsl.insertInto(REJET, REJET.RUN_ID, REJET.SOURCE_RECORD_ID, REJET.MOTIF, REJET.PAYLOAD);
        for (RejetMotive rejet : rejets) {
            insertion = insertion.values(runId, rejet.sourceRecordId(), rejet.motif(), JSONB.valueOf(rejet.payload()));
        }
        return insertion.execute();
    }
}
