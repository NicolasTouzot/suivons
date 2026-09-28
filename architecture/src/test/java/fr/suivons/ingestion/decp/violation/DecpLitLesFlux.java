package fr.suivons.ingestion.decp.violation;

import static fr.suivons.db.jooq.core.Tables.FLUX;

import org.jooq.DSLContext;

/** Cas autorisé : lire core.flux. */
public class DecpLitLesFlux {
    int compter(DSLContext dsl) {
        return dsl.fetchCount(dsl.selectFrom(FLUX));
    }
}
