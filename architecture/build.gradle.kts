// Règles d'architecture du SPEC.md §7.2, vérifiées par ArchUnit sur toutes les classes de production.
plugins {
    id("suivons.java-library")
}

dependencies {
    listOf(
        ":db", ":domain", ":ingestion-core", ":referentiel-client", ":reconciliation",
        ":ingestion-sirene", ":ingestion-decp", ":ingestion-tam", ":ingestion-kohesio",
        ":contract", ":api",
    ).forEach { testImplementation(project(it)) }
    testImplementation(libs.archunit.junit5)
}
