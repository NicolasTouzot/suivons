// Plugins tiers déclarés une seule fois à la racine (versions du catalogue), appliqués par les modules.
// Spring Boot et le codegen jOOQ sont fournis par build-logic (plugins de convention).
plugins {
    alias(libs.plugins.openapi.generator) apply false
}
