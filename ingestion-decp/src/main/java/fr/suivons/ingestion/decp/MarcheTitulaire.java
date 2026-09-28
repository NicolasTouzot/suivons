package fr.suivons.ingestion.decp;

/**
 * Enregistrement DECP après fusion des lignes d'un même couple (marché, titulaire) : un flux par couple
 * (SPEC.md §6.4). C'est ce contenu qui est conservé dans raw.decp_record.
 *
 * @param montant     dernier montant connu, en euros (texte décimal, tel que publié)
 * @param groupement  le titulaire fait partie d'un groupement de co-titulaires (montant non ventilé)
 * @param lignes      nombre de lignes du jeu fusionnées dans cet enregistrement
 */
public record MarcheTitulaire(
        String acheteurId,
        String id,
        String titulaireId,
        String titulaireType,
        String montant,
        String dateNotification,
        String datePublication,
        String objet,
        boolean accordCadre,
        boolean groupement,
        String plateforme,
        int lignes) {

    /** Identifiant stable dans la source : `<SIRET acheteur>|<id>|<identifiant titulaire>` (SPEC.md §6.4). */
    public String sourceRecordId() {
        return acheteurId + "|" + id + "|" + titulaireId;
    }
}
