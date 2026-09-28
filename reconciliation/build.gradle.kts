plugins {
    id("suivons.java-library")
}

dependencies {
    api(project(":domain"))
    implementation(project(":db"))
    api(project(":referentiel-client"))

    integrationTestImplementation(libs.spring.boot.test)
    integrationTestImplementation(libs.testcontainers.postgresql)
    integrationTestImplementation(libs.testcontainers.junit)
    integrationTestImplementation(libs.wiremock)
    integrationTestImplementation(libs.flyway.core)
    integrationTestRuntimeOnly(libs.flyway.postgresql)
    integrationTestRuntimeOnly(libs.postgresql)
}
