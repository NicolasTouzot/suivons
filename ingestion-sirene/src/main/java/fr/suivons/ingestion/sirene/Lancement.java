package fr.suivons.ingestion.sirene;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import fr.suivons.ingestion.core.pipeline.PipelineIngestion.Resultat;
import fr.suivons.ingestion.core.pipeline.SuiviRuns;
import fr.suivons.ingestion.sirene.naf.ChargementNaf;
import fr.suivons.ingestion.sirene.referentiel.RafraichissementReferentiel;

/**
 * Lancement par l'ordonnanceur (`--run`) : chargement de la NAF, puis rafraîchissement du référentiel. Les deux
 * runs sont indépendants ; le code de sortie est 1 si l'un d'eux échoue.
 */
@Component
class Lancement implements ApplicationRunner, ExitCodeGenerator {

    private final ChargementNaf naf;
    private final RafraichissementReferentiel referentiel;
    private final List<Resultat> resultats = new ArrayList<>();

    Lancement(ChargementNaf naf, RafraichissementReferentiel referentiel) {
        this.naf = naf;
        this.referentiel = referentiel;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!arguments.containsOption("run")) {
            return;
        }
        resultats.add(naf.executer());
        resultats.add(referentiel.executer());
    }

    @Override
    public int getExitCode() {
        return resultats.stream().anyMatch(r -> r.statut() == SuiviRuns.Statut.ECHEC) ? 1 : 0;
    }
}
