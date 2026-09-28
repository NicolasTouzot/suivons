package fr.suivons.ingestion.decp;

import java.util.Optional;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import fr.suivons.ingestion.core.pipeline.PipelineIngestion;
import fr.suivons.ingestion.core.pipeline.SuiviRuns;
import fr.suivons.ingestion.core.source.RafraichissementMart;

/**
 * Lancement par l'ordonnanceur : `--run` (ajouter `--tout-retraiter` après une évolution des règles). Le code de
 * sortie est 1 si le run échoue. Pas encore de mart à rafraîchir (lot 3).
 */
@Component
class Lancement implements ApplicationRunner, ExitCodeGenerator {

    private final PipelineIngestion pipeline;
    private final SourceDecp source;
    private final RattacheurSiret rattacheur;
    private Optional<PipelineIngestion.Resultat> resultat = Optional.empty();

    Lancement(PipelineIngestion pipeline, SourceDecp source, RattacheurSiret rattacheur) {
        this.pipeline = pipeline;
        this.source = source;
        this.rattacheur = rattacheur;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!arguments.containsOption("run")) {
            return;
        }
        resultat = Optional.of(pipeline.executer(source, rattacheur, RafraichissementMart.AUCUN,
                new PipelineIngestion.Options(arguments.containsOption("tout-retraiter"))));
    }

    @Override
    public int getExitCode() {
        return resultat.filter(r -> r.statut() == SuiviRuns.Statut.ECHEC).isPresent() ? 1 : 0;
    }
}
