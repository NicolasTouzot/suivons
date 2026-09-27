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
    }
}
