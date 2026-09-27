package fr.suivons.api.signalement.violation;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** Cas autorisé : l'écriture des signalements (ops.signalement) par l'api. */
public class SignalementEcrit {
    void ecrire(DSLContext dsl) {
        dsl.insertInto(DSL.table("ops.signalement")).defaultValues().execute();
    }
}
