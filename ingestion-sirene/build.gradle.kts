// Rafraîchissement du référentiel minimal via l'API Sirene et chargement de la NAF (SPEC.md §7.2).
plugins {
    id("suivons.spring-boot-app")
}

dependencies {
    implementation(project(":ingestion-core"))
    implementation(project(":referentiel-client"))
    implementation(libs.poi.ooxml)

    integrationTestImplementation(libs.wiremock)
}
