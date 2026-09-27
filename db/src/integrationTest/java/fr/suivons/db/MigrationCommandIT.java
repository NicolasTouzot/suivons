package fr.suivons.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Commande de migration de production (ADR 0005) : l'image Flyway officielle appliquée aux migrations
 * du module, comme le fait le service `migrate` du docker-compose.yml.
 */
@Testcontainers
class MigrationCommandIT {

    static final Network RESEAU = Network.newNetwork();

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(System.getProperty("suivons.postgres.image"))
                    .withNetwork(RESEAU)
                    .withNetworkAliases("db");

    @Test
    void appliqueToutesLesMigrationsPuisNeFaitRienAuRejeu() throws SQLException {
        String premier = migrerAvecLImageFlyway();
        int versionsApresPremierPassage = migrationsAppliquees();

        String second = migrerAvecLImageFlyway();

        assertThat(premier).contains("Successfully applied");
        assertThat(versionsApresPremierPassage).isGreaterThanOrEqualTo(5);
        assertThat(second).contains("No migration necessary");
        assertThat(migrationsAppliquees()).isEqualTo(versionsApresPremierPassage);
    }

    private static String migrerAvecLImageFlyway() {
        try (GenericContainer<?> flyway = new GenericContainer<>(System.getProperty("suivons.flyway.image"))
                .withNetwork(RESEAU)
                .withCopyFileToContainer(
                        MountableFile.forHostPath(System.getProperty("suivons.migrations.dir")), "/flyway/migrations")
                .withCommand(
                        "-url=jdbc:postgresql://db:5432/" + POSTGRES.getDatabaseName(),
                        "-user=" + POSTGRES.getUsername(),
                        "-password=" + POSTGRES.getPassword(),
                        "-locations=filesystem:/flyway/migrations",
                        "-cleanDisabled=true",
                        "migrate")
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(2)))) {
            flyway.start();
            return flyway.getLogs();
        }
    }

    private static int migrationsAppliquees() throws SQLException {
        try (Connection c = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                ResultSet rs = c.createStatement().executeQuery(
                        "SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
