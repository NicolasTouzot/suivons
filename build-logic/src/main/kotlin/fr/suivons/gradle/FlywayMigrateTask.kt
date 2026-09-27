package fr.suivons.gradle

import org.flywaydb.core.Flyway
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

/**
 * Commande de migration dédiée (ADR 0005) : applique les migrations Flyway sur une base existante,
 * avec l'utilisateur de migration (propriétaire des schémas). Jamais lancée par une application.
 */
@UntrackedTask(because = "applique les migrations sur une base externe : à exécuter à chaque demande")
abstract class FlywayMigrateTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrations: DirectoryProperty

    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val user: Property<String>

    @get:Internal
    abstract val password: Property<String>

    @TaskAction
    fun migrate() {
        val result = Flyway.configure()
            .dataSource(url.get(), user.get(), password.get())
            .locations("filesystem:" + migrations.get().asFile.absolutePath)
            .cleanDisabled(true)
            .load()
            .migrate()
        logger.lifecycle(
            "Migrations appliquées : {} (version du schéma : {})",
            result.migrationsExecuted,
            result.targetSchemaVersion ?: result.initialSchemaVersion ?: "aucune",
        )
    }
}
