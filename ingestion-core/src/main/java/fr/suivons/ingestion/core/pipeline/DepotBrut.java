package fr.suivons.ingestion.core.pipeline;

import static fr.suivons.db.jooq.ops.Tables.INGESTION_RUN;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Query;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import fr.suivons.ingestion.core.source.EnregistrementSource;

/**
 * Données brutes d'une source (SPEC.md §6.2) : la dernière version reçue de chaque enregistrement, avec le run qui
 * l'a modifiée. Un enregistrement inchangé garde son run d'origine : seules les nouveautés (et les données d'un run
 * qui n'a pas abouti) sont retransformées.
 */
public class DepotBrut {

    private static final Field<String> SOURCE_RECORD_ID = DSL.field(DSL.name("source_record_id"), String.class);
    private static final Field<Long> RUN_ID = DSL.field(DSL.name("run_id"), Long.class);
    private static final Field<JSONB> PAYLOAD = DSL.field(DSL.name("payload"), SQLDataType.JSONB);
    private static final Field<String> CHECKSUM = DSL.field(DSL.name("checksum"), String.class);
    private static final Field<OffsetDateTime> RECU_LE =
            DSL.field(DSL.name("recu_le"), SQLDataType.TIMESTAMPWITHTIMEZONE);

    private final DSLContext dsl;

    public DepotBrut(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Enregistre un lot ; renvoie le nombre d'enregistrements nouveaux ou modifiés. */
    public int enregistrer(String table, long runId, List<? extends EnregistrementSource> lot) {
        Table<?> brute = table(table);
        OffsetDateTime maintenant = OffsetDateTime.now();
        List<Query> requetes = new ArrayList<>(lot.size());
        for (EnregistrementSource enregistrement : lot) {
            requetes.add(dsl.insertInto(brute, SOURCE_RECORD_ID, RUN_ID, PAYLOAD, CHECKSUM, RECU_LE)
                    .values(enregistrement.sourceRecordId(), runId, JSONB.valueOf(enregistrement.payload()),
                            Checksums.sha256(enregistrement.payload()), maintenant)
                    .onConflict(SOURCE_RECORD_ID)
                    .doUpdate()
                    .set(RUN_ID, DSL.excluded(RUN_ID))
                    .set(PAYLOAD, DSL.excluded(PAYLOAD))
                    .set(CHECKSUM, DSL.excluded(CHECKSUM))
                    .set(RECU_LE, DSL.excluded(RECU_LE))
                    .where(DSL.field(DSL.name(brute.getUnqualifiedName(), CHECKSUM.getUnqualifiedName()), String.class)
                            .isDistinctFrom(DSL.excluded(CHECKSUM))));
        }
        int modifies = 0;
        for (int compte : dsl.batch(requetes).execute()) {
            modifies += Math.max(compte, 0);
        }
        return modifies;
    }

    /**
     * Page d'enregistrements à transformer, par ordre d'identifiant : ceux reçus par un run qui n'a pas abouti
     * (le run en cours, mais aussi un run précédent en échec, dont les données brutes n'ont jamais été
     * transformées), ou tous si l'on retraite la source.
     */
    public List<EnregistrementSource> page(String table, String sourceCode, boolean tout, String apres, int taille) {
        var condition = SOURCE_RECORD_ID.gt(apres);
        if (!tout) {
            condition = condition.and(RUN_ID.in(DSL.select(INGESTION_RUN.ID)
                    .from(INGESTION_RUN)
                    .where(INGESTION_RUN.SOURCE_CODE.eq(sourceCode))
                    .and(INGESTION_RUN.STATUT.ne("SUCCES"))));
        }
        return dsl.select(SOURCE_RECORD_ID, PAYLOAD)
                .from(table(table))
                .where(condition)
                .orderBy(SOURCE_RECORD_ID)
                .limit(taille)
                .fetch(r -> new EnregistrementSource(r.value1(), r.value2().data()));
    }

    static Table<?> table(String nomQualifie) {
        String[] parties = nomQualifie.split("\\.");
        if (parties.length != 2 || !"raw".equals(parties[0])) {
            throw new IllegalArgumentException("Table brute attendue dans le schéma raw : " + nomQualifie);
        }
        return DSL.table(DSL.name(parties[0], parties[1]));
    }
}
