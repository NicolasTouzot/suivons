package fr.suivons.api.violation;

import fr.suivons.reconciliation.violation.ReconciliationInterne;

/** Violation volontaire : l'api dépend de la réconciliation. */
public class ApiUtiliseReconciliation {
    ReconciliationInterne reconciliation = new ReconciliationInterne();
}
