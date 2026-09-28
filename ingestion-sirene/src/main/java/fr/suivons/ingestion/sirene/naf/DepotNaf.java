package fr.suivons.ingestion.sirene.naf;

import static fr.suivons.db.jooq.core.Tables.NAF;
import static org.jooq.impl.DSL.excluded;

import java.util.List;

import org.jooq.DSLContext;

/** Écriture de core.naf : une nomenclature est remplacée par le contenu de son fichier. */
public class DepotNaf {

    private final DSLContext dsl;

    public DepotNaf(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Insère ou met à jour les codes, supprime ceux qui ne figurent plus au fichier ; renvoie le nombre de lignes modifiées. */
    public int remplacer(String nomenclature, List<CodeNaf> codes) {
        var insertion = dsl.insertInto(NAF, NAF.NOMENCLATURE, NAF.CODE, NAF.LIBELLE);
        for (CodeNaf code : codes) {
            insertion = insertion.values(nomenclature, code.code(), code.libelle());
        }
        int modifies = insertion.onConflict(NAF.NOMENCLATURE, NAF.CODE)
                .doUpdate()
                .set(NAF.LIBELLE, excluded(NAF.LIBELLE))
                .where(NAF.LIBELLE.isDistinctFrom(excluded(NAF.LIBELLE)))
                .execute();
        int supprimes = dsl.deleteFrom(NAF)
                .where(NAF.NOMENCLATURE.eq(nomenclature))
                .and(NAF.CODE.notIn(codes.stream().map(CodeNaf::code).toList()))
                .execute();
        return modifies + supprimes;
    }
}
