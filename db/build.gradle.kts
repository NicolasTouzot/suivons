plugins {
    id("suivons.jooq-codegen")
}

dependencies {
    integrationTestImplementation(libs.testcontainers.postgresql)
    integrationTestImplementation(libs.testcontainers.junit)
    integrationTestImplementation(libs.flyway.core)
    integrationTestRuntimeOnly(libs.flyway.postgresql)
    integrationTestRuntimeOnly(libs.postgresql)
}
