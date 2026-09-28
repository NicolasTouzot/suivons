package fr.suivons.ingestion.sirene;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Ingestion SIRENE : `--run` charge la NAF et rafraîchit le référentiel minimal, puis l'application s'arrête. */
@SpringBootApplication
public class SireneIngestionApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(SireneIngestionApplication.class, args)));
    }
}
