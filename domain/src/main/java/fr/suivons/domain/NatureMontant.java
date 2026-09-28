package fr.suivons.domain;

/** Nature des bornes d'un montant (SPEC.md §6.4). */
public enum NatureMontant {
    /** Montant certain : borne ferme = borne plafond. */
    FERME,
    /** Montant maximal (accord-cadre, tranche) : borne ferme absente ou basse. */
    PLAFOND,
    /** Montant non ventilé entre plusieurs bénéficiaires : borne ferme absente, plafond = montant total. */
    PARTAGE,
    /** Montant absent de la source. */
    INCONNU
}
