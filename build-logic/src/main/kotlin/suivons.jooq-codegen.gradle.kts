// Code jOOQ généré depuis les migrations Flyway du module (db uniquement).
// Le code généré est versionné : `./gradlew :db:generateJooq` après chaque nouvelle migration.
import fr.suivons.build.GenerateJooqTask

plugins {
    id("suivons.java-library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val generatedDir = layout.projectDirectory.dir("src/main/jooq")

sourceSets.named("main") {
    java.srcDir(generatedDir)
}

dependencies {
    "api"(libs.findLibrary("jooq").get())
}

tasks.register<GenerateJooqTask>("generateJooq") {
    group = "build"
    description = "Régénère le code jOOQ depuis les migrations Flyway (Docker requis)."
    migrations = layout.projectDirectory.dir("src/main/resources/db/migration")
    postgresImage = "postgres:" + libs.findVersion("postgres-image").get().requiredVersion
    schemas = listOf("ops", "raw", "core", "mart")
    packageName = "fr.suivons.db.jooq"
    outputDirectory = generatedDir
}
