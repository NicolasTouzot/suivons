package fr.suivons.ingestion.decp;

import java.time.YearMonth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Réglages de la source DECP (docs/sources/decp.md). Lot 1 bis : un mois du format 2022, lu par l'export JSON filtré
 * de data.economie.gouv.fr ; le jeu complet (Parquet) et le format 2019 arrivent au lot 2.
 *
 * @param jeu            URL du jeu de données dans l'API Explore v2.1 (sans `/exports`)
 * @param pageJeu        page publique du jeu, base des liens vers la source
 * @param mois           mois de notification à charger
 * @param seuilAberrant  montant au-delà duquel un flux est qualifié `ABERRANT` (SPEC.md §6.4, calibré au lot 2)
 */
@ConfigurationProperties("suivons.decp")
public record DecpProperties(
        @DefaultValue("https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/decp-2022-marches-valides")
        String jeu,
        @DefaultValue("https://data.economie.gouv.fr/explore/dataset/decp-2022-marches-valides/table/")
        String pageJeu,
        YearMonth mois,
        @DefaultValue("1000000000") long seuilAberrant) {

    public DecpProperties {
        if (mois == null) {
            throw new IllegalArgumentException("suivons.decp.mois obligatoire (ex. 2026-06)");
        }
    }
}
