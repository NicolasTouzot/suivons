package fr.suivons.ingestion.core.source;

import fr.suivons.domain.FluxNormalise;

/** Résultat de la transformation d'un enregistrement : un flux, ou un rejet motivé tracé dans ops.rejet. */
public sealed interface Transformation {

    static Transformation flux(FluxNormalise flux) {
        return new Flux(flux);
    }

    static Transformation rejet(String sourceRecordId, String motif) {
        return new Rejet(sourceRecordId, motif);
    }

    record Flux(FluxNormalise flux) implements Transformation {
    }

    record Rejet(String sourceRecordId, String motif) implements Transformation {
    }
}
