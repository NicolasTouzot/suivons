package fr.suivons.ingestion.decp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class FusionDecpTest {

    @Test
    void unEnregistrementParCoupleMarcheTitulaireEtGroupementPourLesCoTitulaires() {
        List<MarcheTitulaire> marches = FusionDecp.fusionner(List.of(
                ligne("M1", "100", "2026-06-01", "Sans objet", "11111111111111", "22222222222222", null, null)));

        assertThat(marches).extracting(MarcheTitulaire::sourceRecordId)
                .containsExactly("A|M1|11111111111111", "A|M1|22222222222222");
        assertThat(marches).allSatisfy(m -> {
            assertThat(m.groupement()).isTrue();
            assertThat(m.montant()).isEqualTo("100");
        });
    }

    @Test
    void neRetientQueLaDernierePublicationEtLeMontantLePlusEleveSansJamaisSommer() {
        List<MarcheTitulaire> marches = FusionDecp.fusionner(List.of(
                ligne("M1", "999999", "2026-06-01", "Sans objet", "11111111111111", null, null, null),
                ligne("M1", "150000", "2026-06-20", "Accord-cadre", "11111111111111", null, null, null),
                ligne("M1", "200", "2026-06-20", "Accord-cadre", "11111111111111", null, null, null)));

        assertThat(marches).singleElement().satisfies(m -> {
            assertThat(m.montant()).isEqualTo("150000");
            assertThat(m.accordCadre()).isTrue();
            assertThat(m.groupement()).isFalse();
            assertThat(m.datePublication()).isEqualTo("2026-06-20");
            assertThat(m.lignes()).isEqualTo(3);
        });
    }

    @Test
    void retientLeMontantDeLaModificationLaPlusRecente() {
        List<MarcheTitulaire> marches = FusionDecp.fusionner(List.of(
                ligne("M1", "62474.0", "2026-06-21", "Sans objet", "11111111111111", "1", "70000.0", "2026-07-01"),
                ligne("M1", "62474.0", "2026-06-21", "Sans objet", "11111111111111", "2", "75099.0", "2026-08-12"),
                ligne("M1", "62474.0", "2026-06-21", "Sans objet", "11111111111111", "CDL", "CDL", "CDL")));

        assertThat(marches).singleElement().extracting(MarcheTitulaire::montant).isEqualTo("75099.0");
    }

    @Test
    void ignoreLaSentinelleCdl() {
        LigneDecp ligne = ligne("M1", "10", "2026-06-01", "Sans objet", "11111111111111", "CDL", "CDL", "CDL");

        assertThat(ligne.titulaires()).hasSize(1);
        assertThat(ligne.montantModifie()).isEmpty();
    }

    private static LigneDecp ligne(String id, String montant, String publication, String techniques,
            String titulaire1, String titulaire2OuModification, String montantModification, String dateModification) {
        boolean coTitulaire = titulaire2OuModification != null && titulaire2OuModification.length() == 14;
        return new LigneDecp("A", id, "Objet " + id, techniques, new BigDecimal(montant), "2026-06-01", publication,
                "PLATEFORME", titulaire1, "SIRET", coTitulaire ? titulaire2OuModification : "CDL",
                coTitulaire ? "SIRET" : "CDL", "CDL", "CDL", coTitulaire ? null : titulaire2OuModification,
                montantModification, dateModification);
    }
}
