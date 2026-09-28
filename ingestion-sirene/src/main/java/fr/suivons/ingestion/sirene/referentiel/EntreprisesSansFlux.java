package fr.suivons.ingestion.sirene.referentiel;

import static fr.suivons.db.jooq.core.Tables.ENTREPRISE;
import static fr.suivons.db.jooq.core.Tables.FLUX;
import static org.jooq.impl.DSL.selectOne;

import java.time.OffsetDateTime;
import java.util.List;

import org.jooq.DSLContext;

/** Lecture seule : entreprises du référentiel qui ne sont plus bénéficiaires d'aucun flux (SPEC.md §6.3). */
public class EntreprisesSansFlux {

    private final DSLContext dsl;

    public EntreprisesSansFlux(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** SIREN sans aucun flux, non rafraîchis depuis {@code avant}. */
    public List<String> nonRafraichiesDepuis(OffsetDateTime avant) {
        return dsl.select(ENTREPRISE.SIREN)
                .from(ENTREPRISE)
                .where(ENTREPRISE.RAFRAICHI_LE.lt(avant))
                .andNotExists(selectOne().from(FLUX).where(FLUX.BENEFICIAIRE_SIREN.eq(ENTREPRISE.SIREN)))
                .orderBy(ENTREPRISE.SIREN)
                .fetch(ENTREPRISE.SIREN);
    }
}
