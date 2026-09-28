package fr.suivons.ingestion.core.source;

import java.util.List;

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

    /**
     * Rattachement d'un lot de flux (un chunk du pipeline), dans l'ordre du lot : permet de grouper les appels aux
     * API d'appui (quotas). Une exception fait échouer le run, dont les données brutes seront retraitées.
     */
    default List<Rattachement> rattacherLot(List<FluxNormalise> lot) {
        return lot.stream().map(this::rattacher).toList();
    }
}
