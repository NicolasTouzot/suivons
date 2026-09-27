package fr.suivons.referentiel.violation;

import java.sql.Connection;

/** Violation volontaire : le client du référentiel accède à la base. */
public class ReferentielUtiliseJdbc {
    Connection connexion;
}
