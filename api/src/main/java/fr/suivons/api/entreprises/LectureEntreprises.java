package fr.suivons.api.entreprises;

import static fr.suivons.db.jooq.core.Tables.ENTREPRISE;
import static fr.suivons.db.jooq.core.Tables.FLUX;
import static fr.suivons.db.jooq.core.Tables.NAF;
import static fr.suivons.db.jooq.core.Tables.PAYEUR;
import static fr.suivons.db.jooq.ops.Tables.SOURCE;
import static org.jooq.impl.DSL.coalesce;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.sum;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Component;

import fr.suivons.contract.model.Activite;
import fr.suivons.contract.model.Canal;
import fr.suivons.contract.model.EntrepriseSynthese;
import fr.suivons.contract.model.EtatEntreprise;
import fr.suivons.contract.model.Flux;
import fr.suivons.contract.model.Montants;
import fr.suivons.contract.model.NatureMontant;
import fr.suivons.contract.model.PageFlux;
import fr.suivons.contract.model.Payeur;
import fr.suivons.contract.model.Periode;
import fr.suivons.contract.model.QualiteMontant;
import fr.suivons.contract.model.RepartitionCanal;
import fr.suivons.contract.model.Source;
import fr.suivons.domain.Siren;

/**
 * Lecture des données d'une entreprise dans core (F2, F3). Lot 1 bis : lecture directe des flux ; le mart prendra
 * le relais des agrégats au lot 3. Les flux `ABERRANT` n'entrent dans aucun montant (SPEC.md §6.4) et la
 * dénomination n'est jamais exposée pour une entreprise non diffusible ou une personne physique (SPEC.md §11.2).
 */
@Component
public class LectureEntreprises {

    static final String ANNUAIRE = "https://annuaire-entreprises.data.gouv.fr/entreprise/";
    private static final Condition COMPTE = FLUX.QUALITE.eq("OK");

    private final DSLContext dsl;

    public LectureEntreprises(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<EntrepriseSynthese> synthese(Siren siren) {
        var entreprise = dsl.select(ENTREPRISE.asterisk(), NAF.LIBELLE)
                .from(ENTREPRISE)
                .leftJoin(NAF).on(NAF.NOMENCLATURE.eq(ENTREPRISE.NAF_NOMENCLATURE)).and(NAF.CODE.eq(ENTREPRISE.NAF_CODE))
                .where(ENTREPRISE.SIREN.eq(siren.valeur()))
                .fetchOptional();
        if (entreprise.isEmpty()) {
            return Optional.empty();
        }
        var e = entreprise.get();
        boolean masque = !e.get(ENTREPRISE.DIFFUSIBLE) || e.get(ENTREPRISE.PERSONNE_PHYSIQUE);

        Field<Long> ferme = montant(FLUX.MONTANT_FERME);
        Field<Long> plafond = montant(FLUX.MONTANT_PLAFOND);
        Field<Integer> comptes = count().filterWhere(COMPTE);
        List<RepartitionCanal> canaux = dsl.select(FLUX.CANAL, comptes, ferme, plafond)
                .from(FLUX)
                .where(FLUX.BENEFICIAIRE_SIREN.eq(siren.valeur()))
                .groupBy(FLUX.CANAL)
                .having(comptes.gt(0))
                .orderBy(plafond.desc(), FLUX.CANAL)
                .fetch(r -> new RepartitionCanal(Canal.fromValue(r.value1()), r.value2(),
                        new Montants(r.value3(), r.value4())));

        var totaux = dsl.select(comptes, count().filterWhere(COMPTE.not()), ferme, plafond,
                        min(FLUX.ANNEE).filterWhere(COMPTE), max(FLUX.ANNEE).filterWhere(COMPTE),
                        max(FLUX.EXTRAIT_LE))
                .from(FLUX)
                .where(FLUX.BENEFICIAIRE_SIREN.eq(siren.valeur()))
                .fetchSingle();

        EntrepriseSynthese synthese = new EntrepriseSynthese(
                siren.valeur(),
                masque,
                EtatEntreprise.fromValue(e.get(ENTREPRISE.ETAT)),
                new Montants(totaux.value3(), totaux.value4()),
                totaux.value1(),
                totaux.value2(),
                canaux,
                URI.create(ANNUAIRE + siren.valeur()),
                Optional.ofNullable(totaux.value7()).orElse(e.get(ENTREPRISE.RAFRAICHI_LE)));
        synthese.setDenomination(masque ? null : e.get(ENTREPRISE.DENOMINATION));
        synthese.setCommune(e.get(ENTREPRISE.COMMUNE_SIEGE));
        synthese.setDepartement(e.get(ENTREPRISE.DEPARTEMENT_SIEGE));
        if (e.get(ENTREPRISE.NAF_CODE) != null) {
            synthese.setActivite(new Activite(e.get(ENTREPRISE.NAF_CODE), e.get(ENTREPRISE.NAF_NOMENCLATURE))
                    .libelle(e.get(NAF.LIBELLE)));
        }
        if (totaux.value5() != null) {
            synthese.setPeriode(new Periode(totaux.value5().intValue(), totaux.value6().intValue()));
        }
        return Optional.of(synthese);
    }

    /** Page de flux de l'entreprise, du plus récent au plus ancien ; vide si l'entreprise est inconnue. */
    public Optional<PageFlux> flux(Siren siren, int page, int taille) {
        if (!dsl.fetchExists(ENTREPRISE, ENTREPRISE.SIREN.eq(siren.valeur()))) {
            return Optional.empty();
        }
        Condition beneficiaire = FLUX.BENEFICIAIRE_SIREN.eq(siren.valeur());
        var agregats = dsl.select(count(), max(FLUX.EXTRAIT_LE)).from(FLUX).where(beneficiaire).fetchSingle();
        List<Flux> elements = dsl.select(FLUX.SOURCE_RECORD_ID, FLUX.CANAL, FLUX.DATE_FLUX, PAYEUR.IDENTIFIANT,
                        PAYEUR.NOM, FLUX.OBJET, FLUX.MONTANT_FERME, FLUX.MONTANT_PLAFOND, FLUX.NATURE_MONTANT,
                        FLUX.QUALITE, SOURCE.CODE, SOURCE.LIBELLE, FLUX.SOURCE_URL, FLUX.EXTRAIT_LE)
                .from(FLUX)
                .join(SOURCE).on(SOURCE.CODE.eq(FLUX.SOURCE_CODE))
                .leftJoin(PAYEUR).on(PAYEUR.ID.eq(FLUX.PAYEUR_ID))
                .where(beneficiaire)
                .orderBy(FLUX.DATE_FLUX.desc(), FLUX.SOURCE_CODE, FLUX.SOURCE_RECORD_ID)
                .limit(taille)
                .offset((long) (page - 1) * taille)
                .fetch(r -> {
                    Flux flux = new Flux(r.value1(), Canal.fromValue(r.value2()), r.value3(),
                            NatureMontant.fromValue(r.value9()), QualiteMontant.fromValue(r.value10()),
                            new Source(r.value11(), r.value12(), URI.create(r.value13()), r.value14()));
                    if (r.value4() != null) {
                        flux.setPayeur(new Payeur(r.value4()).nom(r.value5()));
                    }
                    flux.setObjet(r.value6());
                    flux.setMontantFerme(r.value7());
                    flux.setMontantPlafond(r.value8());
                    return flux;
                });
        OffsetDateTime miseAJour = agregats.value2() != null ? agregats.value2()
                : dsl.select(ENTREPRISE.RAFRAICHI_LE).from(ENTREPRISE).where(ENTREPRISE.SIREN.eq(siren.valeur()))
                        .fetchSingle(ENTREPRISE.RAFRAICHI_LE);
        return Optional.of(new PageFlux(elements, page, taille, agregats.value1().longValue(), miseAJour));
    }

    /** Somme des flux comptés (hors `ABERRANT`), une borne absente valant 0. */
    private static Field<Long> montant(Field<Long> borne) {
        Field<BigDecimal> somme = sum(coalesce(borne, inline(0L))).filterWhere(COMPTE);
        return coalesce(somme, inline(BigDecimal.ZERO)).cast(Long.class);
    }
}
