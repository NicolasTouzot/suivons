// Plugins tiers déclarés une seule fois à la racine (versions du catalogue), appliqués par les modules.
// Spring Boot est fourni par build-logic (plugin de convention suivons.spring-boot-app).
plugins {
    alias(libs.plugins.jooq.codegen) apply false
    alias(libs.plugins.openapi.generator) apply false
}
