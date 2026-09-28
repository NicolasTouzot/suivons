package fr.suivons.ingestion.decp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Ingestion DECP : `--run` charge le jeu complet (format 2022), puis l'application s'arrête. */
@SpringBootApplication
public class DecpIngestionApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(DecpIngestionApplication.class, args)));
    }
}
