package fr.suivons.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SirenTest {

    @ParameterizedTest
    @CsvSource({
            "552032534, 552032534",
            "552 032 534, 552032534",
            "'552 032 534', 552032534",
            "55203253400703, 552032534",
            "552 032 534 00703, 552032534"
    })
    void litUnSirenOuUnSiretQuelleQueSoitSaPresentation(String identifiant, String attendu) {
        assertThat(Siren.lire(identifiant)).contains(new Siren(attendu));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "55203253", "552032535", "55203253400704", "ABC032534", "5520325340070"})
    void ignoreUnIdentifiantInvalide(String identifiant) {
        assertThat(Siren.lire(identifiant)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void ignoreUnIdentifiantAbsent() {
        assertThat(Siren.lire(null)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void refuseLaConstructionDUnSirenInvalide() {
        assertThatThrownBy(() -> new Siren("552032535")).isInstanceOf(IdentifiantInvalideException.class);
    }
}
