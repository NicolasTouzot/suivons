package fr.suivons.gradle

import org.flywaydb.core.Flyway
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.SchemaMappingType
import org.jooq.meta.jaxb.Target
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Génère le code jOOQ depuis le schéma réel : PostgreSQL éphémère (Testcontainers),
 * application des migrations Flyway, puis lecture du catalogue. Docker requis.
 */
abstract class GenerateJooqTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrations: DirectoryProperty

    @get:Input
    abstract val postgresImage: Property<String>

    @get:Input
    abstract val schemas: ListProperty<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        PostgreSQLContainer(postgresImage.get()).use { pg ->
            pg.start()
            Flyway.configure()
                .dataSource(pg.jdbcUrl, pg.username, pg.password)
                .locations("filesystem:" + migrations.get().asFile.absolutePath)
                .load()
                .migrate()
            GenerationTool.generate(
                Configuration()
                    .withJdbc(
                        Jdbc()
                            .withDriver("org.postgresql.Driver")
                            .withUrl(pg.jdbcUrl)
                            .withUser(pg.username)
                            .withPassword(pg.password),
                    )
                    .withGenerator(
                        Generator()
                            .withDatabase(
                                Database()
                                    .withName("org.jooq.meta.postgres.PostgresDatabase")
                                    .withSchemata(schemas.get().map { SchemaMappingType().withInputSchema(it) })
                                    .withIncludes(".*")
                                    // Exclus : historique Flyway, métadonnées Spring Batch, partitions de core.flux (accès par la table mère)
                                    .withExcludes("flyway_schema_history|batch_.*|flux_[0-9]{4}|flux_hors_plage"),
                            )
                            .withGenerate(Generate().withJavaTimeTypes(true))
                            .withTarget(
                                Target()
                                    .withPackageName(packageName.get())
                                    .withDirectory(outputDirectory.get().asFile.absolutePath)
                                    .withClean(true),
                            ),
                    ),
            )
        }
    }
}
