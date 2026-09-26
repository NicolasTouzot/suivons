plugins {
    // Téléchargement automatique du JDK de la toolchain (Java 25) s'il n'est pas installé
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "suivons"

// Les modules (SPEC.md §7.2) sont déclarés au lot 0, étape 2.
