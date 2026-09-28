package fr.suivons.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Schéma du lot 1 (SPEC.md §6) : contraintes métier, partitions de core.flux, droits des rôles. */
@Testcontainers
class SchemaIT {

    private static final String CHECK = "23514";
    private static final String UNIQUE = "23505";
    private static final String NOT_NULL = "23502";
    private static final String DENIED = "42501";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(System.getProperty("suivons.postgres.image"));

    static long runId;

    @BeforeAll
    static void migrer() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        executer(proprietaire(),
                "CREATE ROLE ingestion_login LOGIN PASSWORD 'ingestion' IN ROLE suivons_ingestion",
                "CREATE ROLE api_login LOGIN PASSWORD 'api' IN ROLE suivons_api",
                "INSERT INTO core.entreprise (siren, denomination, etat, diffusible, personne_physique, rafraichi_le)"
                        + " VALUES ('552032534', 'DANONE', 'ACTIVE', true, false, now())",
                "INSERT INTO core.payeur (identifiant, nom, type) VALUES ('13000501000033', 'Acheteur', 'ETAT')");
        runId = Long.parseLong(valeurs(proprietaire(),
                "INSERT INTO ops.ingestion_run (source_code) VALUES ('DECP') RETURNING id").getFirst());
    }

    @BeforeEach
    void viderLesFlux() throws SQLException {
        executer(proprietaire(), "DELETE FROM core.flux");
    }

    @Test
    void referenceLesSources() throws SQLException {
        assertThat(valeurs(proprietaire(), "SELECT code FROM ops.source"))
                .containsExactlyInAnyOrder("SIRENE", "RECHERCHE_ENTREPRISES", "DECP", "TAM", "KOHESIO", "NAF");
    }

    @Test
    void creeLesTablesDeSpringBatchDansOps() throws SQLException {
        assertThat(valeurs(proprietaire(),
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'ops' AND table_name LIKE 'batch_%'"))
                .contains("batch_job_instance", "batch_job_execution", "batch_step_execution");
    }

    @Nested
    class Flux {

        @Test
        void rangeChaqueFluxDansLaPartitionDeSonAnnee() throws SQLException {
            inserer(fluxValide("R1", "2024-05-02"));
            inserer(fluxValide("R2", "1999-01-15"));

            assertThat(valeurs(proprietaire(), "SELECT tableoid::regclass::text FROM core.flux ORDER BY source_record_id"))
                    .containsExactly("core.flux_2024", "core.flux_hors_plage");
        }

        @Test
        void refuseUnDoublonDeProvenance() throws SQLException {
            inserer(fluxValide("R1", "2024-05-02"));
            assertEchec(UNIQUE, fluxValide("R1", "2024-06-30"));
        }

        @Test
        void exigeLaProvenance() {
            assertEchec(NOT_NULL, fluxValide("R1", "2024-05-02").replace("'https://source/R1'", "NULL"));
        }

        @Test
        void exigeLaCoherenceEntreAnneeEtDate() {
            assertEchec(CHECK, fluxValide("R1", "2024-05-02").replace("2024::smallint", "2023::smallint"));
        }

        @Test
        void refuseUnMontantNegatifOuUneBorneFermeSuperieureAuPlafond() {
            assertEchec(CHECK, flux("R1", "2024-05-02", "-1", "100", "'PLAFOND'", "'SIREN_SOURCE'", "'552032534'", "1"));
            assertEchec(CHECK, flux("R2", "2024-05-02", "200", "100", "'PLAFOND'", "'SIREN_SOURCE'", "'552032534'", "1"));
        }

        @Test
        void imposeFermeEgalPlafondPourUnMontantFerme() {
            assertEchec(CHECK, flux("R1", "2024-05-02", "50", "100", "'FERME'", "'SIREN_SOURCE'", "'552032534'", "1"));
        }

        @Test
        void accepteUnPlafondSansBorneFerme() {
            assertThatCode(() -> inserer(flux("R1", "2024-05-02", "NULL", "100", "'PLAFOND'", "'SIREN_SOURCE'", "'552032534'", "1")))
                    .doesNotThrowAnyException();
        }

        @Test
        void neRattacheJamaisUnFluxNonResolu() {
            assertEchec(CHECK, flux("R1", "2024-05-02", "100", "100", "'FERME'", "'NON_RESOLU'", "'552032534'", "NULL"));
            assertEchec(CHECK, flux("R2", "2024-05-02", "100", "100", "'FERME'", "'RESOLU_AUTO'", "NULL", "0.95"));
        }

        @Test
        void imposeUneConfianceDeUnPourUnSirenIssuDeLaSource() {
            assertEchec(CHECK, flux("R1", "2024-05-02", "100", "100", "'FERME'", "'SIREN_SOURCE'", "'552032534'", "0.9"));
        }

        private void assertEchec(String sqlState, String insert) {
            SQLException erreur = catchThrowableOfType(SQLException.class, () -> inserer(insert));
            assertThat((Throwable) erreur).as(insert).isNotNull();
            assertThat(erreur.getSQLState()).as(erreur.getMessage()).isEqualTo(sqlState);
        }
    }

    @Nested
    class Roles {

        @Test
        void lIngestionEcritLesDonneesMaisPasLeSchema() throws SQLException {
            assertThatCode(() -> executer(connexion("ingestion_login", "ingestion"),
                    fluxValide("R1", "2024-05-02"),
                    "INSERT INTO ops.ingestion_run (source_code) VALUES ('TAM')",
                    "SELECT nextval('ops.batch_job_execution_seq')"))
                    .doesNotThrowAnyException();
            assertRefuse("ingestion_login", "ingestion", "CREATE TABLE core.interdite (id int)");
            assertRefuse("ingestion_login", "ingestion", "UPDATE ops.source SET libelle = 'x'");
            assertRefuse("ingestion_login", "ingestion", "CREATE TABLE public.interdite (id int)");
        }

        @Test
        void lApiLitLesDonneesServiesSansPouvoirLesModifier() throws SQLException {
            inserer(fluxValide("R1", "2024-05-02"));
            assertThat(valeurs(connexion("api_login", "api"), "SELECT count(*) FROM core.flux")).containsExactly("1");
            assertThat(valeurs(connexion("api_login", "api"), "SELECT count(*) FROM ops.ingestion_run")).isNotEmpty();

            assertRefuse("api_login", "api", fluxValide("R2", "2024-05-02"));
            assertRefuse("api_login", "api", "DELETE FROM core.flux");
            assertRefuse("api_login", "api", "UPDATE core.entreprise SET denomination = 'x'");
            assertRefuse("api_login", "api", "SELECT * FROM ops.rejet");
            assertRefuse("api_login", "api", "CREATE TABLE core.interdite (id int)");
        }

        private void assertRefuse(String utilisateur, String motDePasse, String sql) {
            SQLException erreur = catchThrowableOfType(SQLException.class,
                    () -> executer(connexion(utilisateur, motDePasse), sql));
            assertThat((Throwable) erreur).as(sql).isNotNull();
            assertThat(erreur.getSQLState()).as(erreur.getMessage()).isEqualTo(DENIED);
        }
    }

    private static String fluxValide(String recordId, String date) {
        return flux(recordId, date, "1000", "1000", "'FERME'", "'SIREN_SOURCE'", "'552032534'", "1");
    }

    private static String flux(String recordId, String date, String ferme, String plafond, String nature,
            String rattachement, String siren, String confiance) {
        return """
                INSERT INTO core.flux (canal, beneficiaire_siren, payeur_id, date_flux, annee, montant_ferme,
                    montant_plafond, nature_montant, rattachement, confiance_rattachement, source_code,
                    source_record_id, source_url, run_id, extrait_le)
                VALUES ('MARCHE', %s, (SELECT id FROM core.payeur LIMIT 1), '%s', %s::smallint, %s, %s, %s, %s, %s,
                    'DECP', '%s', 'https://source/%s', %d, now())
                """.formatted(siren, date, date.substring(0, 4), ferme, plafond, nature, rattachement, confiance,
                recordId, recordId, runId);
    }

    private static void inserer(String sql) throws SQLException {
        executer(proprietaire(), sql);
    }

    private static Connection proprietaire() throws SQLException {
        return connexion(POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection connexion(String utilisateur, String motDePasse) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), utilisateur, motDePasse);
    }

    private static void executer(Connection connexion, String... sqls) throws SQLException {
        try (connexion; Statement statement = connexion.createStatement()) {
            for (String sql : sqls) {
                statement.execute(sql);
            }
        }
    }

    private static List<String> valeurs(Connection connexion, String sql) throws SQLException {
        try (connexion; ResultSet rs = connexion.createStatement().executeQuery(sql)) {
            List<String> valeurs = new ArrayList<>();
            while (rs.next()) {
                valeurs.add(rs.getString(1));
            }
            return valeurs;
        }
    }
}
