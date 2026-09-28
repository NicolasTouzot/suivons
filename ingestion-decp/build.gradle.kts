// Source DECP, canal MARCHE (SPEC.md §4).
plugins {
    id("suivons.spring-boot-app")
}

dependencies {
    implementation(project(":ingestion-core"))
    implementation(project(":reconciliation"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)

    integrationTestImplementation(libs.wiremock)
}
