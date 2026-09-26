// Exécutable Spring Boot (api, ingestion-*) : un jar autonome par application.
plugins {
    id("suivons.java-conventions")
    id("org.springframework.boot")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(libs.findLibrary("spring-boot-starter").get())
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
