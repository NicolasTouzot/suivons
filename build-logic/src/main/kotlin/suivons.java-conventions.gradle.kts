// Socle commun à tous les modules Java : toolchain, compilation, BOM Spring Boot, suites de tests.
plugins {
    java
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val springBootBom = libs.findLibrary("spring-boot-bom").get()
val assertj = libs.findLibrary("assertj").get()

group = "fr.suivons"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing"))
}

dependencies {
    // Les versions gérées par Spring Boot s'appliquent à tous les modules (y compris domain, sans en tirer Spring)
    implementation(platform(springBootBom))
}

testing {
    suites {
        // TU : lancés par `./gradlew build`
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
            dependencies {
                implementation(platform(springBootBom))
                implementation(assertj)
            }
        }
        // TI : lancés par `./gradlew integrationTest`, hors `build` (Docker requis pour Testcontainers)
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(platform(springBootBom))
                implementation(assertj)
            }
            targets.configureEach {
                testTask.configure {
                    shouldRunAfter(tasks.named("test"))
                    // Image PostgreSQL unique pour tous les TI (Testcontainers), issue du catalogue
                    systemProperty(
                        "suivons.postgres.image",
                        "postgres:" + libs.findVersion("postgres-image").get().requiredVersion,
                    )
                }
            }
        }
    }
}
