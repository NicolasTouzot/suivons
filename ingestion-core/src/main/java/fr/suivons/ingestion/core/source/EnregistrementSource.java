package fr.suivons.ingestion.core.source;

/**
 * Enregistrement tel que reçu : conservé dans la table brute de la source pour pouvoir rejouer la transformation.
 *
 * @param sourceRecordId identifiant stable dans la source (clé d'idempotence)
 * @param payload        contenu reçu, en JSON
 */
public record EnregistrementSource(String sourceRecordId, String payload) {

    public EnregistrementSource {
        if (sourceRecordId == null || sourceRecordId.isBlank()) {
            throw new IllegalArgumentException("Identifiant d'enregistrement source absent");
        }
        if (payload == null) {
            throw new IllegalArgumentException("Contenu d'enregistrement source absent : " + sourceRecordId);
        }
    }
}
