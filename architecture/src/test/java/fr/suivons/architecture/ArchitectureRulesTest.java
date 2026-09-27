package fr.suivons.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/** Vérifie les règles d'architecture sur toutes les classes de production de fr.suivons. */
class ArchitectureRulesTest {

    static JavaClasses production;

    @BeforeAll
    static void importer() {
        production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("fr.suivons");
    }

    @Test
    void lImportCouvreLesModulesExistants() {
        assertThat(production.stream().map(JavaClass::getPackageName).collect(Collectors.toSet()))
                .contains("fr.suivons.api", "fr.suivons.ingestion.decp", "fr.suivons.ingestion.sirene",
                        "fr.suivons.ingestion.tam", "fr.suivons.ingestion.kohesio");
        assertThat(production.stream().map(JavaClass::getPackageName))
                .as("aucune classe de test importée")
                .noneMatch(p -> p.contains(".violation"));
    }

    @Test
    void modulesRespectentLaMatrice() {
        ArchitectureRules.MODULES_RESPECTENT_LA_MATRICE.check(production);
    }

    @Test
    void sourcesIndependantes() {
        ArchitectureRules.SOURCES_INDEPENDANTES.check(production);
    }

    @Test
    void apiSansIngestionNiReconciliation() {
        ArchitectureRules.API_SANS_INGESTION_NI_RECONCILIATION.check(production);
    }

    @Test
    void domainePur() {
        ArchitectureRules.DOMAINE_PUR.check(production);
    }

    @Test
    void referentielSansBase() {
        ArchitectureRules.REFERENTIEL_SANS_BASE.check(production);
    }

    @Test
    void pasDeJpa() {
        ArchitectureRules.PAS_DE_JPA.check(production);
    }

    @Test
    void apiEcritSeulementLesSignalements() {
        ArchitectureRules.API_ECRIT_SEULEMENT_LES_SIGNALEMENTS.check(production);
    }
}
