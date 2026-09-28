// Exécutable Spring Boot (api, ingestion-*) : un jar autonome par application.
plugins {
    id("suivons.java-conventions")
    id("org.springframework.boot")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(libs.findLibrary("spring-boot-starter").get())
}

// Version et nom exposés par /actuator/info (sans horodatage, pour un build reproductible)
springBoot {
    buildInfo {
        excludes = setOf("time")
    }
}

testing {
    suites {
        withType<JvmTestSuite>().configureEach {
            dependencies {
                implementation(libs.findLibrary("spring-boot-starter-test").get())
            }
        }
        // Chaque application démarre en TI sur un vrai PostgreSQL, schéma appliqué par Flyway (jamais en production)
        named<JvmTestSuite>("integrationTest") {
            dependencies {
                implementation(libs.findLibrary("spring-boot-testcontainers").get())
                implementation(libs.findLibrary("testcontainers-postgresql").get())
                implementation(libs.findLibrary("testcontainers-junit").get())
                runtimeOnly(libs.findLibrary("spring-boot-starter-flyway").get())
                runtimeOnly(libs.findLibrary("flyway-postgresql").get())
            }
        }
    }
}
