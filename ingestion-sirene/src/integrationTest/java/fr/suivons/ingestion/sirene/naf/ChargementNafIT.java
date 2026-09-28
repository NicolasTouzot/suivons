package fr.suivons.ingestion.sirene.naf;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static fr.suivons.db.jooq.core.Tables.NAF;
import static fr.suivons.db.jooq.ops.Tables.INGESTION_RUN;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import fr.suivons.ingestion.core.pipeline.PipelineIngestion.Resultat;
import fr.suivons.ingestion.core.pipeline.SuiviRuns;
import fr.suivons.ingestion.core.pipeline.Telechargeur;
import fr.suivons.ingestion.sirene.Fixtures;

/** Chargement de core.naf depuis les vrais fichiers de l'INSEE (/fixtures/naf), servis par WireMock. */
@SpringBootTest
@Testcontainers
class ChargementNafIT {

    static final String SHA_NAF_REV2 = "76088b7b66dc72e106ec047902069f3744237acb9f249ca3f3b98c273bf3785b";
    static final String SHA_NAF_2025 = "6bbb9b9affc7d547f7b2b4a790043d6c942362d743bf38377d5e32bd7ceba2d7";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(System.getProperty("suivons.postgres.image"));

    @RegisterExtension
    static WireMockExtension insee = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @TempDir
    static Path cache;

    @DynamicPropertySource
    static void reglages(DynamicPropertyRegistry registre) {
        registre.add("suivons.ingestion.repertoire-cache", cache::toString);
        // Une liste redéfinie remplace entièrement celle d'application.yaml ; l'espace encodé du nom de fichier
        // reproduit l'adresse réelle de la NAF 2025
        fichier(registre, 0, "NAFRev2", "/naf2008_liste_n5.xls", "Feuil1", SHA_NAF_REV2);
        fichier(registre, 1, "NAF2025", "/Structure%20NAF%202025.xlsx", "Sous-classes", SHA_NAF_2025);
    }

    static void fichier(DynamicPropertyRegistry registre, int rang, String nomenclature, String chemin,
            String feuille, String sha256) {
        String prefixe = "suivons.naf.fichiers[" + rang + "].";
        registre.add(prefixe + "nomenclature", () -> nomenclature);
        registre.add(prefixe + "url", () -> insee.baseUrl() + chemin);
        registre.add(prefixe + "feuille", () -> feuille);
        registre.add(prefixe + "sha256", () -> sha256);
    }

    @Autowired
    ChargementNaf chargement;

    @Autowired
    DSLContext dsl;

    @Autowired
    Telechargeur telechargeur;

    @Autowired
    SuiviRuns runs;

    @Autowired
    PlatformTransactionManager transactions;

    @BeforeEach
    void servirLesFichiers() {
        dsl.deleteFrom(NAF).execute();
        insee.stubFor(get("/naf2008_liste_n5.xls")
                .willReturn(aResponse().withBody(Fixtures.octets("naf/naf2008_liste_n5.xls"))));
        insee.stubFor(get("/Structure%20NAF%202025.xlsx")
                .willReturn(aResponse().withBody(Fixtures.octets("naf/naf2025_structure.xlsx"))));
    }

    @Test
    void chargeLesSousClassesDesDeuxNomenclatures() {
        Resultat resultat = chargement.executer();

        assertThat(resultat.statut()).isEqualTo(SuiviRuns.Statut.SUCCES);
        assertThat(resultat.compteurs()).isEqualTo(new SuiviRuns.Compteurs(732 + 747, 732 + 747, 0));
        assertThat(compter("NAFRev2")).isEqualTo(732);
        assertThat(compter("NAF2025")).isEqualTo(747);
        assertThat(libelle("NAFRev2", "62.01Z")).isEqualTo("Programmation informatique");
        assertThat(libelle("NAFRev2", "70.10Z")).isEqualTo("Activités des sièges sociaux");
        assertThat(libelle("NAF2025", "01.11Y"))
                .isEqualTo("Culture de céréales, à l’exception du riz, de légumineuses et de graines oléagineuses");
        assertThat(dsl.select(INGESTION_RUN.SOURCE_CODE, INGESTION_RUN.VERSION_SOURCE, INGESTION_RUN.CHECKSUM_FICHIER)
                .from(INGESTION_RUN).where(INGESTION_RUN.ID.eq(resultat.runId())).fetchOne())
                .satisfies(run -> {
                    assertThat(run.value1()).isEqualTo(ChargementNaf.SOURCE);
                    assertThat(run.value2()).isEqualTo("NAFRev2,NAF2025");
                    assertThat(run.value3()).hasSize(64);
                });
    }

    @Test
    void rejouerLeChargementNeChangeRien() {
        chargement.executer();
        dsl.update(NAF).set(NAF.LIBELLE, "Libellé périmé").where(NAF.CODE.eq("62.01Z")).execute();
        dsl.insertInto(NAF, NAF.NOMENCLATURE, NAF.CODE, NAF.LIBELLE).values("NAFRev2", "00.00Z", "Code retiré")
                .execute();

        Resultat second = chargement.executer();
        Resultat troisieme = chargement.executer();

        assertThat(second.compteurs().charges()).isEqualTo(2);
        assertThat(libelle("NAFRev2", "62.01Z")).isEqualTo("Programmation informatique");
        assertThat(compter("NAFRev2")).isEqualTo(732);
        assertThat(troisieme.statut()).isEqualTo(SuiviRuns.Statut.SUCCES);
        assertThat(troisieme.compteurs().charges()).isZero();
    }

    @Test
    void refuseUnFichierDontLEmpreinteAChange() {
        chargement.executer();
        ChargementNaf avecAutreEmpreinte = new ChargementNaf(new NafProperties(List.of(
                new NafProperties.FichierNaf("NAFRev2", insee.baseUrl() + "/naf2008_liste_n5.xls",
                        "Feuil1", SHA_NAF_2025))),
                telechargeur, runs, new DepotNaf(dsl), new TransactionTemplate(transactions), cache);
        dsl.update(NAF).set(NAF.LIBELLE, "Libellé local").where(NAF.CODE.eq("62.01Z")).execute();

        Resultat resultat = avecAutreEmpreinte.executer();

        assertThat(resultat.statut()).isEqualTo(SuiviRuns.Statut.ECHEC);
        assertThat(libelle("NAFRev2", "62.01Z")).isEqualTo("Libellé local");
        assertThat(compter("NAF2025")).isEqualTo(747);
    }

    private int compter(String nomenclature) {
        return dsl.fetchCount(NAF, NAF.NOMENCLATURE.eq(nomenclature));
    }

    private String libelle(String nomenclature, String code) {
        return dsl.select(NAF.LIBELLE).from(NAF)
                .where(NAF.NOMENCLATURE.eq(nomenclature)).and(NAF.CODE.eq(code))
                .fetchSingle(NAF.LIBELLE);
    }
}
