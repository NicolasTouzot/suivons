// Module db uniquement : migrations Flyway, commande de migration dédiée et code jOOQ généré.
// Le code généré est versionné : `./gradlew :db:generateJooq` après chaque nouvelle migration.
import fr.suivons.gradle.FlywayMigrateTask
import fr.suivons.gradle.GenerateJooqTask

plugins {
    id("suivons.java-library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val generatedDir = layout.projectDirectory.dir("src/main/jooq")
val migrationsDir = layout.projectDirectory.dir("src/main/resources/db/migration")

sourceSets.named("main") {
    java.srcDir(generatedDir)
}

dependencies {
    "api"(libs.findLibrary("jooq").get())
}

val generateJooq = tasks.register<GenerateJooqTask>("generateJooq") {
    group = "build"
    description = "Régénère le code jOOQ depuis les migrations Flyway (Docker requis)."
    migrations = migrationsDir
    postgresImage = "postgres:" + libs.findVersion("postgres-image").get().requiredVersion
    schemas = listOf("ops", "raw", "core", "mart")
    packageName = "fr.suivons.db.jooq"
    outputDirectory = generatedDir
}

// Génération manuelle (Docker requis), jamais déclenchée par la compilation ; si les deux sont
// demandées dans la même commande, la compilation lit le code fraîchement généré.
tasks.named("compileJava") {
    mustRunAfter(generateJooq)
}

// Commande de migration dédiée (ADR 0005). Par défaut : base du docker-compose.yml (dev) ;
// ailleurs : SUIVONS_DB_URL, SUIVONS_DB_MIGRATION_USER, SUIVONS_DB_MIGRATION_PASSWORD.
tasks.register<FlywayMigrateTask>("migrate") {
    group = "database"
    description = "Applique les migrations Flyway sur la base configurée (utilisateur de migration)."
    migrations = migrationsDir
    url = providers.environmentVariable("SUIVONS_DB_URL").orElse("jdbc:postgresql://localhost:5432/suivons")
    user = providers.environmentVariable("SUIVONS_DB_MIGRATION_USER").orElse("suivons")
    password = providers.environmentVariable("SUIVONS_DB_MIGRATION_PASSWORD").orElse("suivons")
}

tasks.withType<Test>().configureEach {
    // Commande de production (image Flyway officielle) éprouvée en TI sur les migrations du module
    systemProperty("suivons.flyway.image", "flyway/flyway:" + libs.findVersion("flyway-image").get().requiredVersion)
    systemProperty("suivons.migrations.dir", migrationsDir.asFile.absolutePath)
}
