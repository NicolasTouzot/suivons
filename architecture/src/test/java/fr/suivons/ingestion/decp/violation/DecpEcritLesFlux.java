package fr.suivons.ingestion.decp.violation;

import static fr.suivons.db.jooq.core.Tables.FLUX;

import org.jooq.DSLContext;

/** Violation volontaire : une source écrit directement dans core.flux au lieu de passer par ingestion-core. */
public class DecpEcritLesFlux {
    void ecrire(DSLContext dsl) {
        dsl.deleteFrom(FLUX).where(FLUX.SOURCE_CODE.eq("DECP")).execute();
    }
}
