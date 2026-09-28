package fr.suivons.ingestion.core.source;

import fr.suivons.domain.FluxNormalise;

/**
 * Rattachement d'un flux à un SIREN (SPEC.md §6.5), fourni par le module reconciliation. Le rattacheur garantit
 * que le SIREN retenu existe dans core.entreprise. Par défaut, rien n'est rattaché ({@link #AUCUN}).
 */
@FunctionalInterface
public interface Rattacheur {

    /** Rattacheur prudent par défaut : aucun flux n'est rattaché. */
    Rattacheur AUCUN = flux -> Rattachement.nonResolu();

    Rattachement rattacher(FluxNormalise flux);
}
