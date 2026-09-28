package fr.suivons.ingestion.core.violation;

import static fr.suivons.db.jooq.core.Tables.FLUX;

import org.jooq.DSLContext;

/** Cas autorisé : ingestion-core écrit dans core.flux. */
public class CoreEcritLesFlux {
    void ecrire(DSLContext dsl) {
        dsl.deleteFrom(FLUX).where(FLUX.SOURCE_CODE.eq("TEST")).execute();
    }
}
