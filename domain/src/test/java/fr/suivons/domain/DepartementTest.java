package fr.suivons.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DepartementTest {

    @ParameterizedTest
    @CsvSource({"75112, 75", "63164, 63", "2A004, 2A", "2B033, 2B", "97411, 974", "97101, 971", "98735, 987"})
    void deduitLeDepartementDuCodeCommune(String codeCommune, String departement) {
        assertThat(Departement.depuisCodeCommune(codeCommune)).contains(departement);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[ND]", "7511", "ABCDE", ""})
    void ignoreUnCodeCommuneInexploitable(String codeCommune) {
        assertThat(Departement.depuisCodeCommune(codeCommune)).isEmpty();
    }

    @Test
    void ignoreUnCodeCommuneAbsent() {
        assertThat(Departement.depuisCodeCommune(null)).isEmpty();
    }
}
