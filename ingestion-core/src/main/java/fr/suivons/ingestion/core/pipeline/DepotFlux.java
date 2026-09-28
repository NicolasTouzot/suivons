package fr.suivons.ingestion.core.pipeline;

import static fr.suivons.db.jooq.core.Tables.FLUX;
import static fr.suivons.db.jooq.core.Tables.PAYEUR;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.impl.DSL;

import fr.suivons.domain.FluxNormalise;
import fr.suivons.domain.Payeur;
import fr.suivons.domain.Siren;
import fr.suivons.ingestion.core.source.Rattachement;

/**
 * Seul point d'écriture de core.flux (CLAUDE.md, règle vérifiée par ArchUnit). Écriture idempotente par
 * (source_code, source_record_id) : un flux identique n'est pas réécrit (sa provenance reste celle du run qui l'a
 * créé ou modifié), un flux qui change d'année remplace sa ligne de l'année précédente.
 */
public class DepotFlux {

    private final DSLContext dsl;

    public DepotFlux(DSLContext dsl) {
        this.dsl = dsl;
    }

    public record FluxRattache(FluxNormalise flux, Rattachement rattachement) {
    }

    /** Écrit un lot ; renvoie le nombre de flux créés ou modifiés. */
    public int ecrire(String sourceCode, long runId, OffsetDateTime extraitLe, List<FluxRattache> lot) {
        Map<String, Long> payeurs = payeurs(lot);
        List<Query> requetes = new ArrayList<>(lot.size() * 2);
        for (FluxRattache element : lot) {
            FluxNormalise flux = element.flux();
            Rattachement rattachement = element.rattachement();
            short annee = (short) flux.annee();
            Long payeurId = flux.payeur().map(p -> payeurs.get(p.identifiant())).orElse(null);
            String siren = rattachement.siren().map(Siren::valeur).orElse(null);
            BigDecimal confiance = rattachement.confiance().orElse(null);

            requetes.add(dsl.deleteFrom(FLUX)
                    .where(FLUX.SOURCE_CODE.eq(sourceCode))
                    .and(FLUX.SOURCE_RECORD_ID.eq(flux.sourceRecordId()))
                    .and(FLUX.ANNEE.ne(annee)));
            requetes.add(dsl.insertInto(FLUX)
                    .set(FLUX.CANAL, flux.canal().name())
                    .set(FLUX.BENEFICIAIRE_SIREN, siren)
                    .set(FLUX.BENEFICIAIRE_NOM_SOURCE, flux.beneficiaireNomSource().orElse(null))
                    .set(FLUX.PAYEUR_ID, payeurId)
                    .set(FLUX.OBJET, flux.objet().orElse(null))
                    .set(FLUX.DATE_FLUX, flux.dateFlux())
                    .set(FLUX.ANNEE, annee)
                    .set(FLUX.MONTANT_FERME, flux.montantFerme().orElse(null))
                    .set(FLUX.MONTANT_PLAFOND, flux.montantPlafond().orElse(null))
                    .set(FLUX.NATURE_MONTANT, flux.nature().name())
                    .set(FLUX.QUALITE, flux.qualite().name())
                    .set(FLUX.RATTACHEMENT, rattachement.statut().name())
                    .set(FLUX.CONFIANCE_RATTACHEMENT, confiance)
                    .set(FLUX.SOURCE_CODE, sourceCode)
                    .set(FLUX.SOURCE_RECORD_ID, flux.sourceRecordId())
                    .set(FLUX.SOURCE_URL, flux.sourceUrl())
                    .set(FLUX.RUN_ID, runId)
                    .set(FLUX.EXTRAIT_LE, extraitLe)
                    .onConflict(FLUX.SOURCE_CODE, FLUX.SOURCE_RECORD_ID, FLUX.ANNEE)
                    .doUpdate()
                    .set(FLUX.CANAL, DSL.excluded(FLUX.CANAL))
                    .set(FLUX.BENEFICIAIRE_SIREN, DSL.excluded(FLUX.BENEFICIAIRE_SIREN))
                    .set(FLUX.BENEFICIAIRE_NOM_SOURCE, DSL.excluded(FLUX.BENEFICIAIRE_NOM_SOURCE))
                    .set(FLUX.PAYEUR_ID, DSL.excluded(FLUX.PAYEUR_ID))
                    .set(FLUX.OBJET, DSL.excluded(FLUX.OBJET))
                    .set(FLUX.DATE_FLUX, DSL.excluded(FLUX.DATE_FLUX))
                    .set(FLUX.MONTANT_FERME, DSL.excluded(FLUX.MONTANT_FERME))
                    .set(FLUX.MONTANT_PLAFOND, DSL.excluded(FLUX.MONTANT_PLAFOND))
                    .set(FLUX.NATURE_MONTANT, DSL.excluded(FLUX.NATURE_MONTANT))
                    .set(FLUX.QUALITE, DSL.excluded(FLUX.QUALITE))
                    .set(FLUX.RATTACHEMENT, DSL.excluded(FLUX.RATTACHEMENT))
                    .set(FLUX.CONFIANCE_RATTACHEMENT, DSL.excluded(FLUX.CONFIANCE_RATTACHEMENT))
                    .set(FLUX.SOURCE_URL, DSL.excluded(FLUX.SOURCE_URL))
                    .set(FLUX.RUN_ID, DSL.excluded(FLUX.RUN_ID))
                    .set(FLUX.EXTRAIT_LE, DSL.excluded(FLUX.EXTRAIT_LE))
                    // Rien à écrire si le contenu est identique : rejouer un run ne change rien
                    .where(DSL.row(FLUX.CANAL, FLUX.BENEFICIAIRE_SIREN, FLUX.BENEFICIAIRE_NOM_SOURCE, FLUX.PAYEUR_ID,
                                    FLUX.OBJET, FLUX.DATE_FLUX, FLUX.MONTANT_FERME, FLUX.MONTANT_PLAFOND,
                                    FLUX.NATURE_MONTANT, FLUX.QUALITE, FLUX.RATTACHEMENT,
                                    FLUX.CONFIANCE_RATTACHEMENT, FLUX.SOURCE_URL)
                            .isDistinctFrom(DSL.row(DSL.excluded(FLUX.CANAL), DSL.excluded(FLUX.BENEFICIAIRE_SIREN),
                                    DSL.excluded(FLUX.BENEFICIAIRE_NOM_SOURCE), DSL.excluded(FLUX.PAYEUR_ID),
                                    DSL.excluded(FLUX.OBJET), DSL.excluded(FLUX.DATE_FLUX),
                                    DSL.excluded(FLUX.MONTANT_FERME), DSL.excluded(FLUX.MONTANT_PLAFOND),
                                    DSL.excluded(FLUX.NATURE_MONTANT), DSL.excluded(FLUX.QUALITE),
                                    DSL.excluded(FLUX.RATTACHEMENT), DSL.excluded(FLUX.CONFIANCE_RATTACHEMENT),
                                    DSL.excluded(FLUX.SOURCE_URL)))));
        }
        int[] comptes = dsl.batch(requetes).execute();
        int modifies = 0;
        for (int i = 1; i < comptes.length; i += 2) {
            modifies += Math.max(comptes[i], 0);
        }
        return modifies;
    }

    /** Crée ou complète les payeurs du lot ; renvoie leur identifiant technique. */
    private Map<String, Long> payeurs(List<FluxRattache> lot) {
        Map<String, Long> ids = new HashMap<>();
        for (FluxRattache element : lot) {
            element.flux().payeur().ifPresent(payeur -> ids.computeIfAbsent(payeur.identifiant(),
                    identifiant -> enregistrerPayeur(payeur)));
        }
        return ids;
    }

    private long enregistrerPayeur(Payeur payeur) {
        return dsl.insertInto(PAYEUR)
                .set(PAYEUR.IDENTIFIANT, payeur.identifiant())
                .set(PAYEUR.NOM, payeur.nom().orElse(null))
                .set(PAYEUR.TYPE, payeur.type().name())
                .onConflict(PAYEUR.IDENTIFIANT)
                .doUpdate()
                .set(PAYEUR.NOM, DSL.coalesce(DSL.excluded(PAYEUR.NOM), PAYEUR.NOM))
                .returning(PAYEUR.ID)
                .fetchSingle(PAYEUR.ID);
    }
}
