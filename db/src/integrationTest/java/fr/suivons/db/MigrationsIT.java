package fr.suivons.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigrationsIT {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(System.getProperty("suivons.postgres.image"));

    static Flyway flyway;

    @BeforeAll
    static void migrate() {
        flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load();
        flyway.migrate();
    }

    @Test
    void creeLesQuatreSchemas() throws SQLException {
        assertThat(query("select schema_name from information_schema.schemata"))
                .contains("ops", "raw", "core", "mart");
    }

    @Test
    void installeLesExtensionsDeRecherche() throws SQLException {
        assertThat(query("select extname from pg_extension")).contains("pg_trgm", "unaccent");
    }

    @Test
    void rejouerLesMigrationsNeChangeRien() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void tourneSurLaVersionMajeureAttendue() throws SQLException {
        assertThat(query("show server_version").getFirst()).startsWith("18.");
    }

    private static List<String> query(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                ResultSet rs = c.createStatement().executeQuery(sql)) {
            List<String> values = new ArrayList<>();
            while (rs.next()) {
                values.add(rs.getString(1));
            }
            return values;
        }
    }
}
