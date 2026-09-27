package fr.suivons.domain.violation;

import org.jooq.impl.DSL;

/** Violation volontaire : le domaine dépend de jOOQ. */
public class DomaineUtiliseJooq {
    Object champ = DSL.field("montant");
}
