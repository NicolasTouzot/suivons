package fr.suivons.domain;

/** Manière dont un flux est rattaché à un SIREN (SPEC.md §6.5) ; en cas de doute, NON_RESOLU. */
public enum StatutRattachement {
    SIREN_SOURCE,
    RESOLU_AUTO,
    NON_RESOLU
}
