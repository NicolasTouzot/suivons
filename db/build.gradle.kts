plugins {
    id("suivons.database")
}

dependencies {
    integrationTestImplementation(libs.testcontainers.postgresql)
    integrationTestImplementation(libs.testcontainers.junit)
    integrationTestImplementation(libs.flyway.core)
    integrationTestRuntimeOnly(libs.flyway.postgresql)
    integrationTestRuntimeOnly(libs.postgresql)
}
