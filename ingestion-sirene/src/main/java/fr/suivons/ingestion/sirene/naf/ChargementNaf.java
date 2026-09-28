package fr.suivons.ingestion.sirene.naf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;

import fr.suivons.ingestion.core.pipeline.Checksums;
import fr.suivons.ingestion.core.pipeline.PipelineIngestion.Resultat;
import fr.suivons.ingestion.core.pipeline.SuiviRuns;
import fr.suivons.ingestion.core.pipeline.Telechargeur;

/**
 * Chargement de core.naf depuis les fichiers de l'INSEE (source `NAF`, un run dans ops.ingestion_run).
 * Chaque fichier est contrôlé par son empreinte : un fichier modifié fait échouer le run sans rien changer.
 * Toutes les nomenclatures sont écrites dans une seule transaction.
 */
public class ChargementNaf {

    public static final String SOURCE = "NAF";
    private static final Logger LOG = LoggerFactory.getLogger(ChargementNaf.class);

    private final NafProperties reglages;
    private final Telechargeur telechargeur;
    private final SuiviRuns runs;
    private final DepotNaf depot;
    private final TransactionTemplate transaction;
    private final Path repertoireCache;

    public ChargementNaf(NafProperties reglages, Telechargeur telechargeur, SuiviRuns runs, DepotNaf depot,
            TransactionTemplate transaction, Path repertoireCache) {
        this.reglages = reglages;
        this.telechargeur = telechargeur;
        this.runs = runs;
        this.depot = depot;
        this.transaction = transaction;
        this.repertoireCache = repertoireCache.resolve("naf");
    }

    public Resultat executer() {
        long runId = runs.demarrer(SOURCE);
        int lus = 0;
        int charges = 0;
        try {
            List<Lecture> lectures = new ArrayList<>();
            List<String> empreintes = new ArrayList<>();
            for (NafProperties.FichierNaf fichier : reglages.fichiers()) {
                Path local = telechargeur.telecharger(fichier.uri(), repertoireCache.resolve(nomLocal(fichier)));
                String empreinte = Checksums.sha256(local);
                if (!empreinte.equalsIgnoreCase(fichier.sha256())) {
                    throw new IllegalStateException("Fichier " + fichier.nomenclature() + " modifié par l'INSEE "
                            + "(empreinte " + empreinte + ", attendue " + fichier.sha256()
                            + ") : vérifier son contenu puis mettre à jour suivons.naf.fichiers");
                }
                empreintes.add(empreinte);
                lectures.add(new Lecture(fichier.nomenclature(), LecteurNaf.lire(local, fichier.feuille())));
            }
            runs.noterFichier(runId, Checksums.sha256(String.join(",", empreintes)),
                    String.join(",", lectures.stream().map(Lecture::nomenclature).toList()));
            lus = lectures.stream().mapToInt(l -> l.codes().size()).sum();
            charges = transaction.execute(statut -> lectures.stream()
                    .mapToInt(l -> depot.remplacer(l.nomenclature(), l.codes()))
                    .sum());
            SuiviRuns.Compteurs compteurs = new SuiviRuns.Compteurs(lus, charges, 0);
            runs.terminer(runId, SuiviRuns.Statut.SUCCES, compteurs);
            LOG.info("NAF : run {} terminé, {} codes lus, {} lignes modifiées", runId, lus, charges);
            return new Resultat(runId, SuiviRuns.Statut.SUCCES, false, compteurs);
        } catch (Exception e) {
            LOG.error("NAF : run {} en échec", runId, e);
            SuiviRuns.Compteurs compteurs = new SuiviRuns.Compteurs(lus, 0, 0);
            runs.terminer(runId, SuiviRuns.Statut.ECHEC, compteurs);
            return new Resultat(runId, SuiviRuns.Statut.ECHEC, false, compteurs);
        }
    }

    private static String nomLocal(NafProperties.FichierNaf fichier) {
        String chemin = fichier.uri().getPath();
        String extension = chemin.substring(chemin.lastIndexOf('.'));
        return fichier.nomenclature() + extension;
    }

    private record Lecture(String nomenclature, List<CodeNaf> codes) {
    }
}
