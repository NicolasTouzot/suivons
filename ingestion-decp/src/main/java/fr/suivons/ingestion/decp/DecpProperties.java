package fr.suivons.ingestion.decp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages de la source DECP (docs/sources/decp.md) : jeu complet au format 2022, export Parquet de
 * data.economie.gouv.fr (le format 2019 arrive à l'étape 2 du lot 2).
 *
 * @param jeu            URL du jeu de données dans l'API Explore v2.1 (sans `/exports`)
 * @param pageJeu        page publique du jeu, base des liens vers la source
 * @param seuilAberrant  montant au-delà duquel un flux est qualifié `ABERRANT` (SPEC.md §6.4, calibré au lot 2)
 */
@ConfigurationProperties("suivons.decp")
public record DecpProperties(
        @DefaultValue("https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/decp-2022-marches-valides")
        String jeu,
        @DefaultValue("https://data.economie.gouv.fr/explore/dataset/decp-2022-marches-valides/table/")
        String pageJeu,
        @DefaultValue("1000000000") long seuilAberrant) {
}
