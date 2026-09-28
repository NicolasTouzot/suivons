package fr.suivons.domain;

/** Qualité d'un montant : un montant ABERRANT reste visible mais n'entre dans aucun agrégat (SPEC.md §6.4). */
public enum QualiteMontant {
    OK,
    ABERRANT
}
