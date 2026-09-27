pluginManagement {
    includeBuild("build-logic")
}

plugins {
    // Téléchargement automatique du JDK de la toolchain (Java 25) s'il n'est pas installé
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "suivons"

// Modules : SPEC.md §7.2 (le front Angular est un projet npm à part, dans /front)
include(
    "db",
    "domain",
    "ingestion-core",
    "referentiel-client",
    "reconciliation",
    "ingestion-sirene",
    "ingestion-decp",
    "ingestion-tam",
    "ingestion-kohesio",
    "contract",
    "api",
    // Tests d'architecture transverses (ArchUnit), sans code de production
    "architecture",
)
