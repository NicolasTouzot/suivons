// Plugins de convention partagés par les modules (java, bibliothèque, application Spring Boot, codegen jOOQ).
plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.gradle.plugin)
    // Codegen jOOQ : PostgreSQL éphémère (Testcontainers) + migrations Flyway, versions du BOM Spring Boot
    implementation(libs.testcontainers.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.postgresql)
    implementation(libs.jooq.codegen)
}
