// Plugins déclarés une seule fois à la racine (versions du catalogue), appliqués par les modules.
plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.jooq.codegen) apply false
    alias(libs.plugins.openapi.generator) apply false
}
