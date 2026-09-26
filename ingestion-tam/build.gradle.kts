// Source TAM, canal AIDE_ETAT (SPEC.md §4).
plugins {
    id("suivons.spring-boot-app")
}

dependencies {
    implementation(project(":ingestion-core"))
    implementation(project(":reconciliation"))
}
