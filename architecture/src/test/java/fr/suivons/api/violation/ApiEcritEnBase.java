package fr.suivons.api.violation;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** Violation volontaire : l'api écrit en base hors du paquet signalement. */
public class ApiEcritEnBase {
    void ecrire(DSLContext dsl) {
        dsl.insertInto(DSL.table("core.flux")).defaultValues().execute();
    }
}
