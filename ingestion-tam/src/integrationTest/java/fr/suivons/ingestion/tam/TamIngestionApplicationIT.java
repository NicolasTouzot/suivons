package fr.suivons.ingestion.tam;

import static org.assertj.core.api.Assertions.assertThat;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** L'application démarre sur PostgreSQL (schéma migré, jOOQ opérationnel), sans serveur web. */
@SpringBootTest
@Testcontainers
class TamIngestionApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(System.getProperty("suivons.postgres.image"));

    @Autowired
    ApplicationContext contexte;

    @Test
    void demarreSansServeurWebEtAccedeALaBase() {
        assertThat(contexte.getClass().getSimpleName()).doesNotContain("Web");
        assertThat(contexte.getBean(DSLContext.class).fetchValue("SELECT count(*) FROM ops.source", Long.class))
                .isEqualTo(5L);
    }
}
