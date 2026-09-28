package fr.suivons.reconciliation.referentiel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import fr.suivons.domain.Siren;
import fr.suivons.referentiel.sirene.ActivitePrincipale;
import fr.suivons.referentiel.sirene.Siege;
import fr.suivons.referentiel.sirene.UniteLegale;

class EntrepriseReferentielTest {

    private static final Siren SIREN = new Siren("552032534");
    private static final Optional<Siege> SIEGE =
            Optional.of(new Siege(SIREN, "55203253400703", Optional.of("75109"), Optional.of("PARIS")));

    @Test
    void conserveLIdentiteMinimaleDUneSocieteDiffusible() {
        EntrepriseReferentiel entreprise = EntrepriseReferentiel.depuis(
                unite(true, "5599", new ActivitePrincipale("70.10Z", "NAFRev2"), true), SIEGE);

        assertThat(entreprise.denomination()).contains("DANONE");
        assertThat(entreprise.naf()).contains(new ActivitePrincipale("70.10Z", "NAFRev2"));
        assertThat(entreprise.communeSiege()).contains("PARIS");
        assertThat(entreprise.departementSiege()).contains("75");
        assertThat(entreprise.etat()).isEqualTo("ACTIVE");
        assertThat(entreprise.diffusible()).isTrue();
        assertThat(entreprise.personnePhysique()).isFalse();
    }

    @Test
    void neConserveJamaisLaDenominationDUneEntrepriseNonDiffusible() {
        EntrepriseReferentiel entreprise = EntrepriseReferentiel.depuis(
                unite(false, "5710", new ActivitePrincipale("70.22Z", "NAFRev2"), true), SIEGE);

        assertThat(entreprise.denomination()).isEmpty();
        assertThat(entreprise.diffusible()).isFalse();
    }

    @Test
    void neConserveJamaisLaDenominationDUnePersonnePhysique() {
        EntrepriseReferentiel entreprise = EntrepriseReferentiel.depuis(
                unite(true, UniteLegale.CATEGORIE_ENTREPRENEUR_INDIVIDUEL,
                        new ActivitePrincipale("43.91A", "NAFRev2"), true), SIEGE);

        assertThat(entreprise.denomination()).isEmpty();
        assertThat(entreprise.personnePhysique()).isTrue();
    }

    @Test
    void ignoreUneActiviteCodeeDansUneNomenclatureAnterieure() {
        EntrepriseReferentiel entreprise = EntrepriseReferentiel.depuis(
                unite(true, "5599", new ActivitePrincipale("74.1J", "NAFRev1"), false), Optional.empty());

        assertThat(entreprise.naf()).isEmpty();
        assertThat(entreprise.communeSiege()).isEmpty();
        assertThat(entreprise.departementSiege()).isEmpty();
        assertThat(entreprise.etat()).isEqualTo("CESSEE");
    }

    @Test
    void accepteLaNaf2025() {
        EntrepriseReferentiel entreprise = EntrepriseReferentiel.depuis(
                unite(true, "5599", new ActivitePrincipale("70.10Y", "NAF2025"), true), SIEGE);

        assertThat(entreprise.naf()).contains(new ActivitePrincipale("70.10Y", "NAF2025"));
    }

    private static UniteLegale unite(boolean diffusible, String categorieJuridique, ActivitePrincipale activite,
            boolean active) {
        return new UniteLegale(SIREN, diffusible, Optional.of("DANONE"), Optional.of(categorieJuridique),
                Optional.of(activite), active, Optional.of("GE"));
    }
}
