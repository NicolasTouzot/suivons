plugins {
    id("suivons.java-library")
}

dependencies {
    api(project(":db"))
    api(project(":domain"))

    api(libs.spring.boot.starter.batch.jdbc)
    api(libs.spring.boot.starter.jooq)
    runtimeOnly(libs.postgresql)

    integrationTestImplementation(libs.spring.boot.starter.test)
    integrationTestImplementation(libs.spring.boot.testcontainers)
    integrationTestImplementation(libs.testcontainers.postgresql)
    integrationTestImplementation(libs.testcontainers.junit)
    integrationTestImplementation(libs.wiremock)
    integrationTestImplementation(libs.jackson.databind)
    integrationTestRuntimeOnly(libs.spring.boot.starter.flyway)
    integrationTestRuntimeOnly(libs.flyway.postgresql)
}
