package fr.suivons.ingestion.sirene.referentiel;

import static fr.suivons.db.jooq.core.Tables.ENTREPRISE;
import static org.jooq.impl.DSL.row;

import java.time.OffsetDateTime;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Query;

import fr.suivons.domain.Siren;
import fr.suivons.referentiel.sirene.ActivitePrincipale;

/** Lecture et mise à jour du référentiel minimal (core.entreprise). */
public class DepotEntreprises {

    private final DSLContext dsl;

    public DepotEntreprises(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** SIREN du référentiel, par pages dans l'ordre croissant. */
    public List<Siren> page(String apres, int taille) {
        return dsl.select(ENTREPRISE.SIREN)
                .from(ENTREPRISE)
                .where(ENTREPRISE.SIREN.gt(apres))
                .orderBy(ENTREPRISE.SIREN)
                .limit(taille)
                .fetch(r -> new Siren(r.value1()));
    }

    /**
     * Retire les entreprises désignées (sans flux, voir {@link EntreprisesSansFlux}). La clé étrangère de core.flux
     * interdit de retirer une entreprise qui aurait reçu un flux entre-temps : le run échoue alors sans rien retirer.
     */
    public int retirer(List<String> sirens) {
        return sirens.isEmpty() ? 0 : dsl.deleteFrom(ENTREPRISE).where(ENTREPRISE.SIREN.in(sirens)).execute();
    }

    /**
     * Met à jour les entreprises lues dans Sirene : seules les lignes dont une valeur change sont réécrites, puis
     * toutes sont marquées rafraîchies. Renvoie le nombre d'entreprises modifiées.
     */
    public int enregistrer(List<EntrepriseReferentiel> entreprises, OffsetDateTime maintenant) {
        if (entreprises.isEmpty()) {
            return 0;
        }
        List<Query> miseAJour = entreprises.stream().map(e -> {
            String nafCode = e.naf().map(ActivitePrincipale::code).orElse(null);
            String nafNomenclature = e.naf().map(ActivitePrincipale::nomenclature).orElse(null);
            String denomination = e.denomination().orElse(null);
            String commune = e.communeSiege().orElse(null);
            String departement = e.departementSiege().orElse(null);
            return (Query) dsl.update(ENTREPRISE)
                    .set(ENTREPRISE.DENOMINATION, denomination)
                    .set(ENTREPRISE.NAF_CODE, nafCode)
                    .set(ENTREPRISE.NAF_NOMENCLATURE, nafNomenclature)
                    .set(ENTREPRISE.COMMUNE_SIEGE, commune)
                    .set(ENTREPRISE.DEPARTEMENT_SIEGE, departement)
                    .set(ENTREPRISE.ETAT, e.etat())
                    .set(ENTREPRISE.DIFFUSIBLE, e.diffusible())
                    .set(ENTREPRISE.PERSONNE_PHYSIQUE, e.personnePhysique())
                    .where(ENTREPRISE.SIREN.eq(e.siren().valeur()))
                    .and(row(ENTREPRISE.DENOMINATION, ENTREPRISE.NAF_CODE, ENTREPRISE.NAF_NOMENCLATURE,
                            ENTREPRISE.COMMUNE_SIEGE, ENTREPRISE.DEPARTEMENT_SIEGE, ENTREPRISE.ETAT,
                            ENTREPRISE.DIFFUSIBLE, ENTREPRISE.PERSONNE_PHYSIQUE)
                            .isDistinctFrom(denomination, nafCode, nafNomenclature, commune, departement, e.etat(),
                                    e.diffusible(), e.personnePhysique()));
        }).toList();
        int modifiees = 0;
        for (int lignes : dsl.batch(miseAJour).execute()) {
            modifiees += lignes;
        }
        dsl.update(ENTREPRISE)
                .set(ENTREPRISE.RAFRAICHI_LE, maintenant)
                .where(ENTREPRISE.SIREN.in(entreprises.stream().map(e -> e.siren().valeur()).toList()))
                .execute();
        return modifiees;
    }
}
