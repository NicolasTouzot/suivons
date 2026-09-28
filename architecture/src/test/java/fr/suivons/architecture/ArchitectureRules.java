package fr.suivons.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.Optional;
import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;

/** Règles d'architecture non négociables (SPEC.md §7.2, CLAUDE.md). */
final class ArchitectureRules {

    private ArchitectureRules() {
    }

    /** Matrice des dépendances entre modules (SPEC.md §7.2), dépendances transitives déclarées incluses. */
    static final ArchRule MODULES_RESPECTENT_LA_MATRICE = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            // Modules encore sans classes au lot 0
            .withOptionalLayers(true)
            .layer("domain").definedBy("fr.suivons.domain..")
            .layer("db").definedBy("fr.suivons.db..")
            .layer("contract").definedBy("fr.suivons.contract..")
            .layer("referentiel-client").definedBy("fr.suivons.referentiel..")
            .layer("reconciliation").definedBy("fr.suivons.reconciliation..")
            .layer("ingestion-core").definedBy("fr.suivons.ingestion.core..")
            .layer("ingestion-sources").definedBy(sourcesIngestion())
            .layer("api").definedBy("fr.suivons.api..")
            .whereLayer("api").mayNotBeAccessedByAnyLayer()
            .whereLayer("ingestion-sources").mayNotBeAccessedByAnyLayer()
            .whereLayer("ingestion-core").mayOnlyBeAccessedByLayers("ingestion-sources")
            .whereLayer("reconciliation").mayOnlyBeAccessedByLayers("ingestion-sources")
            .whereLayer("referentiel-client")
            .mayOnlyBeAccessedByLayers("reconciliation", "ingestion-sources", "api")
            .whereLayer("contract").mayOnlyBeAccessedByLayers("api")
            .whereLayer("db")
            .mayOnlyBeAccessedByLayers("ingestion-core", "reconciliation", "ingestion-sources", "api")
            .because("les dépendances entre modules sont limitées à celles du SPEC.md §7.2");

    /** Un module ingestion-<source> ne dépend jamais d'un autre. */
    static final ArchRule SOURCES_INDEPENDANTES = slices()
            .assignedFrom(new SliceAssignment() {
                @Override
                public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
                    return sourceOf(javaClass).map(SliceIdentifier::of).orElse(SliceIdentifier.ignore());
                }

                @Override
                public String getDescription() {
                    return "modules ingestion-<source>";
                }
            })
            .should().notDependOnEachOther()
            .because("chaque source d'ingestion est un exécutable indépendant (SPEC.md §7.3)");

    /** L'api ne dépend ni des ingestions, ni de la réconciliation. */
    static final ArchRule API_SANS_INGESTION_NI_RECONCILIATION = noClasses()
            .that().resideInAPackage("fr.suivons.api..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("fr.suivons.ingestion..", "fr.suivons.reconciliation..")
            .because("l'api lit les données produites, elle ne les produit pas (SPEC.md §7.2)");

    /** Le domaine reste pur : ni Spring, ni jOOQ, ni SQL, ni autre module. */
    static final ArchRule DOMAINE_PUR = noClasses()
            .that().resideInAPackage("fr.suivons.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..", "org.jooq..", "java.sql..", "javax.sql..",
                    "fr.suivons.db..", "fr.suivons.contract..", "fr.suivons.referentiel..",
                    "fr.suivons.reconciliation..", "fr.suivons.ingestion..", "fr.suivons.api..")
            .because("domain ne contient que des types et règles métier purs (SPEC.md §7.2)");

    /** Le client du référentiel n'accède jamais à la base (ADR 0004). */
    static final ArchRule REFERENTIEL_SANS_BASE = noClasses()
            .that().resideInAPackage("fr.suivons.referentiel..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                    "fr.suivons.db..", "org.jooq..", "java.sql..", "javax.sql..",
                    "org.springframework.jdbc..")
            .because("referentiel-client n'écrit jamais en base (ADR 0004)");

    /** Accès aux données par jOOQ uniquement. */
    static final ArchRule PAS_DE_JPA = noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "javax.persistence..", "org.hibernate..")
            .because("l'accès aux données passe uniquement par jOOQ (CLAUDE.md)");

    /** L'api n'écrit que dans ops.signalement : écritures jOOQ confinées au paquet signalement. */
    static final ArchRule API_ECRIT_SEULEMENT_LES_SIGNALEMENTS = noClasses()
            .that().resideInAPackage("fr.suivons.api..")
            .and().resideOutsideOfPackage("fr.suivons.api.signalement..")
            .should().callMethodWhere(ecritureJooq())
            .because("l'api se connecte en lecture seule, sauf sur ops.signalement (SPEC.md §8)");

    /** Seul ingestion-core écrit dans core.flux (CLAUDE.md, SPEC.md §7.2) ; les autres modules peuvent le lire. */
    static final ArchRule SEUL_INGESTION_CORE_ECRIT_LES_FLUX = classes()
            .that().resideOutsideOfPackages("fr.suivons.ingestion.core..", "fr.suivons.db.jooq..")
            .should(new ArchCondition<JavaClass>("ne pas écrire dans core.flux") {
                @Override
                public void check(JavaClass classe, ConditionEvents evenements) {
                    boolean utiliseLesFlux = classe.getDirectDependenciesFromSelf().stream()
                            .anyMatch(dependance -> TABLE_FLUX.contains(dependance.getTargetClass().getName()));
                    if (!utiliseLesFlux) {
                        return;
                    }
                    classe.getMethodCallsFromSelf().stream()
                            .filter(ecritureJooq())
                            .forEach(appel -> evenements.add(SimpleConditionEvent.violated(appel,
                                    appel.getDescription() + " : écriture jOOQ dans une classe qui manipule core.flux")));
                }
            })
            .because("seul ingestion-core écrit dans core.flux (provenance et idempotence garanties par le pipeline)");

    private static final Set<String> TABLE_FLUX = Set.of(
            "fr.suivons.db.jooq.core.tables.Flux", "fr.suivons.db.jooq.core.tables.records.FluxRecord");

    private static final Set<String> ECRITURES_DSL =
            Set.of("insertInto", "update", "delete", "deleteFrom", "mergeInto", "truncate", "execute", "batch");
    private static final Set<String> ECRITURES_RECORD = Set.of("store", "insert", "update", "delete", "merge");

    private static DescribedPredicate<JavaMethodCall> ecritureJooq() {
        return DescribedPredicate.describe("une écriture jOOQ", call -> {
            JavaClass owner = call.getTargetOwner();
            String method = call.getName();
            return (owner.isAssignableTo("org.jooq.DSLContext") && ECRITURES_DSL.contains(method))
                    || (owner.isAssignableTo("org.jooq.UpdatableRecord") && ECRITURES_RECORD.contains(method));
        });
    }

    private static DescribedPredicate<JavaClass> sourcesIngestion() {
        return DescribedPredicate.describe("modules ingestion-<source>", c -> sourceOf(c).isPresent());
    }

    /** Nom de la source pour une classe de fr.suivons.ingestion.<source>.., hors ingestion-core. */
    static Optional<String> sourceOf(JavaClass javaClass) {
        String prefix = "fr.suivons.ingestion.";
        String pkg = javaClass.getPackageName();
        if (!pkg.startsWith(prefix)) {
            return Optional.empty();
        }
        String source = pkg.substring(prefix.length()).split("\\.")[0];
        return "core".equals(source) ? Optional.empty() : Optional.of(source);
    }
}
