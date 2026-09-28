package fr.suivons.ingestion.sirene.referentiel;

import java.util.Optional;
import java.util.Set;

import fr.suivons.domain.Siren;
import fr.suivons.referentiel.sirene.ActivitePrincipale;
import fr.suivons.referentiel.sirene.Siege;
import fr.suivons.referentiel.sirene.UniteLegale;

/**
 * Ligne de core.entreprise : les seuls champs d'identité conservés localement (SPEC.md §6.3, ADR 0004).
 * La dénomination d'une entreprise non diffusible ou d'une personne physique n'est jamais conservée (SPEC §11.2).
 */
public record EntrepriseReferentiel(
        Siren siren,
        Optional<String> denomination,
        Optional<ActivitePrincipale> naf,
        Optional<String> communeSiege,
        Optional<String> departementSiege,
        boolean active,
        boolean diffusible,
        boolean personnePhysique) {

    /** Nomenclatures de core.naf ; une activité codée dans une nomenclature antérieure n'est pas conservée. */
    public static final Set<String> NOMENCLATURES = Set.of("NAFRev2", "NAF2025");

    public static EntrepriseReferentiel depuis(UniteLegale unite, Optional<Siege> siege) {
        boolean nommable = unite.diffusible() && !unite.personnePhysique();
        return new EntrepriseReferentiel(
                unite.siren(),
                nommable ? unite.denomination() : Optional.empty(),
                unite.activitePrincipale().filter(a -> NOMENCLATURES.contains(a.nomenclature())),
                siege.flatMap(Siege::libelleCommune),
                siege.flatMap(Siege::departement),
                unite.active(),
                unite.diffusible(),
                unite.personnePhysique());
    }

    public String etat() {
        return active ? "ACTIVE" : "CESSEE";
    }
}
