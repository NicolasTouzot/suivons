package fr.suivons.ingestion.decp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import fr.suivons.domain.FluxNormalise;
import fr.suivons.domain.NatureMontant;
import fr.suivons.domain.QualiteMontant;
import fr.suivons.domain.Siren;
import fr.suivons.ingestion.core.source.Transformation;

class TransformationDecpTest {

    private final TransformationDecp transformation = new TransformationDecp(new DecpProperties(
            "https://decp.test/jeu", "https://decp.test/page/", 1_000_000_000L));

    @Test
    void marcheOrdinaireAMontantFermeArrondiALEuro() {
        FluxNormalise flux = flux(marche("105855.5", false, false, "55203253400703", "SIRET"));

        assertThat(flux.nature()).isEqualTo(NatureMontant.FERME);
        assertThat(flux.montantFerme()).contains(105856L);
        assertThat(flux.montantPlafond()).contains(105856L);
        assertThat(flux.qualite()).isEqualTo(QualiteMontant.OK);
        assertThat(flux.sirenSource()).contains(new Siren("552032534"));
        assertThat(flux.payeur()).hasValueSatisfying(p -> assertThat(p.identifiant()).isEqualTo("21280201100016"));
        assertThat(flux.sourceUrl()).startsWith("https://decp.test/page/?q=")
                .contains("M1").contains("21280201100016");
    }

    @Test
    void accordCadreEnPlafondEtGroupementEnPartage() {
        FluxNormalise accordCadre = flux(marche("5000", true, false, "55203253400703", "SIRET"));
        FluxNormalise groupement = flux(marche("5000", true, true, "55203253400703", "SIRET"));

        assertThat(accordCadre.nature()).isEqualTo(NatureMontant.PLAFOND);
        assertThat(accordCadre.montantFerme()).isEmpty();
        assertThat(accordCadre.montantPlafond()).contains(5000L);
        assertThat(groupement.nature()).isEqualTo(NatureMontant.PARTAGE);
        assertThat(groupement.montantFerme()).isEmpty();
    }

    @Test
    void montantsAberrantsConservesMaisQualifies() {
        assertThat(flux(marche("1.0", false, false, "55203253400703", "SIRET")).qualite())
                .isEqualTo(QualiteMontant.ABERRANT);
        assertThat(flux(marche("1500000000", true, false, "55203253400703", "SIRET")).qualite())
                .isEqualTo(QualiteMontant.ABERRANT);
    }

    @Test
    void seulUnSiretValideDonneUnSirenSource() {
        assertThat(flux(marche("10", false, false, "PT510406670", "TVA")).sirenSource()).isEmpty();
        assertThat(flux(marche("10", false, false, "55203253400703123", "SIRET")).sirenSource()).isEmpty();
    }

    @Test
    void rejetteUnMontantOuUneDateInvalide() {
        assertThat(transformation.transformer(marche("", false, false, "55203253400703", "SIRET")))
                .isInstanceOf(Transformation.Rejet.class);
        MarcheTitulaire sansDate = new MarcheTitulaire("21280201100016", "M1", "55203253400703", "SIRET", "10", "",
                "2026-06-16", "Objet", false, false, "AIFE", 1);
        assertThat(transformation.transformer(sansDate)).isInstanceOf(Transformation.Rejet.class);
    }

    private FluxNormalise flux(MarcheTitulaire marche) {
        Transformation resultat = transformation.transformer(marche);
        assertThat(resultat).isInstanceOf(Transformation.Flux.class);
        return ((Transformation.Flux) resultat).flux();
    }

    private static MarcheTitulaire marche(String montant, boolean accordCadre, boolean groupement, String titulaire,
            String type) {
        return new MarcheTitulaire("21280201100016", "M1", titulaire, type, montant, "2026-06-10", "2026-06-16",
                "Objet", accordCadre, groupement, "AIFE", 1);
    }
}
