package fr.suivons.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

import fr.suivons.api.signalement.violation.SignalementEcrit;
import fr.suivons.api.violation.ApiEcritEnBase;
import fr.suivons.api.violation.ApiUtiliseReconciliation;
import fr.suivons.db.violation.DbUtiliseApi;
import fr.suivons.domain.violation.DomaineUtiliseJooq;
import fr.suivons.ingestion.decp.violation.DecpUtiliseTam;
import fr.suivons.ingestion.tam.violation.TamInterne;
import fr.suivons.reconciliation.violation.ReconciliationInterne;
import fr.suivons.referentiel.violation.ReferentielUtiliseJdbc;

/** Les règles détectent bien les violations : chaque règle est confrontée à une infraction volontaire. */
class ArchitectureRulesSelfTest {

    @Test
    void detecteUneSourceQuiDependDUneAutre() {
        assertViolation(ArchitectureRules.SOURCES_INDEPENDANTES, DecpUtiliseTam.class, TamInterne.class);
    }

    @Test
    void detecteLApiQuiDependDeLaReconciliation() {
        assertViolation(ArchitectureRules.API_SANS_INGESTION_NI_RECONCILIATION,
                ApiUtiliseReconciliation.class, ReconciliationInterne.class);
    }

    @Test
    void detecteUnDomaineQuiDependDeJooq() {
        assertViolation(ArchitectureRules.DOMAINE_PUR, DomaineUtiliseJooq.class);
    }

    @Test
    void detecteUnReferentielQuiAccedeALaBase() {
        assertViolation(ArchitectureRules.REFERENTIEL_SANS_BASE, ReferentielUtiliseJdbc.class);
    }

    @Test
    void detecteUneEcritureDeLApiHorsSignalements() {
        assertViolation(ArchitectureRules.API_ECRIT_SEULEMENT_LES_SIGNALEMENTS, ApiEcritEnBase.class);
    }

    @Test
    void autoriseLEcritureDesSignalements() {
        JavaClasses classes = new ClassFileImporter().importClasses(SignalementEcrit.class);
        assertThat(ArchitectureRules.API_ECRIT_SEULEMENT_LES_SIGNALEMENTS.evaluate(classes).hasViolation())
                .isFalse();
    }

    @Test
    void detecteUneDependanceHorsMatrice() {
        assertViolation(ArchitectureRules.MODULES_RESPECTENT_LA_MATRICE,
                DbUtiliseApi.class, ApiUtiliseReconciliation.class);
        assertViolation(ArchitectureRules.MODULES_RESPECTENT_LA_MATRICE,
                ApiUtiliseReconciliation.class, ReconciliationInterne.class);
    }

    private static void assertViolation(ArchRule rule, Class<?>... classes) {
        JavaClasses imported = new ClassFileImporter().importClasses(classes);
        assertThat(rule.evaluate(imported).hasViolation()).as(rule.getDescription()).isTrue();
    }
}
