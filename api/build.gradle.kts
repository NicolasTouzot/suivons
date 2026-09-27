// API REST en lecture (+ signalements) (SPEC.md §8).
plugins {
    id("suivons.spring-boot-app")
}

dependencies {
    implementation(project(":db"))
    implementation(project(":domain"))
    implementation(project(":contract"))
    implementation(project(":referentiel-client"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jooq)
    runtimeOnly(libs.postgresql)

    integrationTestImplementation(libs.spring.boot.testcontainers)
    integrationTestImplementation(libs.testcontainers.postgresql)
    integrationTestImplementation(libs.testcontainers.junit)
    integrationTestImplementation(libs.snakeyaml)
    // L'API ne migre jamais le schéma (rôle en lecture seule) : Flyway n'est présent qu'en TI
    integrationTestRuntimeOnly(libs.spring.boot.starter.flyway)
    integrationTestRuntimeOnly(libs.flyway.postgresql)
}
