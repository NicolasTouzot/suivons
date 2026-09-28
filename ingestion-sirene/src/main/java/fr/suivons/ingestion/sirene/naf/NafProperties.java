package fr.suivons.ingestion.sirene.naf;

import java.net.URI;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fichiers de nomenclature NAF publiés par l'INSEE (SPEC.md §6.3), chargés dans core.naf.
 *
 * @param fichiers un fichier par nomenclature
 */
@ConfigurationProperties("suivons.naf")
public record NafProperties(List<FichierNaf> fichiers) {

    public NafProperties {
        fichiers = fichiers == null ? List.of() : List.copyOf(fichiers);
    }

    /**
     * Fichier d'une nomenclature.
     *
     * @param nomenclature `NAFRev2` ou `NAF2025`, comme dans l'API Sirene
     * @param url          adresse du fichier sur insee.fr (.xls ou .xlsx), déjà encodée : lue telle quelle
     * @param feuille      feuille des sous-classes (code en colonne A, libellé en colonne B)
     * @param sha256       empreinte attendue : un fichier modifié par l'INSEE est refusé jusqu'à vérification
     */
    public record FichierNaf(String nomenclature, String url, String feuille, String sha256) {

        public FichierNaf {
            if (nomenclature == null || url == null || feuille == null || sha256 == null) {
                throw new IllegalArgumentException("Fichier NAF incomplet (nomenclature, url, feuille et sha256 "
                        + "obligatoires) : " + nomenclature + ", " + url);
            }
        }

        /** Adresse sans réencodage (une conversion automatique en URI transformerait `%20` en `%2520`). */
        public URI uri() {
            return URI.create(url);
        }
    }
}
