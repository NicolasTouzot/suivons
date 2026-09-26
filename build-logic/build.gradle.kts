// Plugins de convention partagés par les modules (java, bibliothèque, application Spring Boot).
plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.spring.boot.gradle.plugin)
}
