package fr.suivons.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Flux d'argent public issu d'un enregistrement source, avant rattachement (SPEC.md §3, §6.3, §6.4).
 * Les règles de bornes sont vérifiées à la construction : un flux incohérent n'existe pas.
 *
 * @param sourceRecordId        identifiant stable de l'enregistrement dans la source (clé d'idempotence)
 * @param sourceUrl             lien vers l'enregistrement ou le jeu de données d'origine
 * @param sirenSource           SIREN fourni par la source, s'il y en a un (le rattachement le vérifie)
 * @param beneficiaireNomSource nom du bénéficiaire tel qu'écrit dans la source
 * @param montantFerme          borne basse, en euros
 * @param montantPlafond        borne haute, en euros
 */
public record FluxNormalise(
        String sourceRecordId,
        String sourceUrl,
        Canal canal,
        Optional<Siren> sirenSource,
        Optional<String> beneficiaireNomSource,
        Optional<Payeur> payeur,
        Optional<String> objet,
        LocalDate dateFlux,
        Optional<Long> montantFerme,
        Optional<Long> montantPlafond,
        NatureMontant nature,
        QualiteMontant qualite) {

    public FluxNormalise {
        exiger(sourceRecordId != null && !sourceRecordId.isBlank(), "identifiant source absent");
        exiger(sourceUrl != null && !sourceUrl.isBlank(), "lien vers la source absent");
        Objects.requireNonNull(canal, "canal");
        Objects.requireNonNull(sirenSource, "sirenSource");
        Objects.requireNonNull(beneficiaireNomSource, "beneficiaireNomSource");
        Objects.requireNonNull(payeur, "payeur");
        Objects.requireNonNull(objet, "objet");
        exiger(dateFlux != null, "date du flux absente");
        Objects.requireNonNull(montantFerme, "montantFerme");
        Objects.requireNonNull(montantPlafond, "montantPlafond");
        Objects.requireNonNull(nature, "nature");
        Objects.requireNonNull(qualite, "qualite");

        exiger(montantFerme.orElse(0L) >= 0 && montantPlafond.orElse(0L) >= 0, "montant négatif");
        exiger(nature == NatureMontant.INCONNU || montantPlafond.isPresent(), "borne plafond absente");
        montantFerme.ifPresent(ferme -> exiger(montantPlafond.map(plafond -> ferme <= plafond).orElse(false),
                "borne ferme supérieure au plafond"));
        exiger(nature != NatureMontant.FERME || montantFerme.isPresent() && montantFerme.equals(montantPlafond),
                "montant FERME sans bornes égales");
    }

    /** Année de rattachement du flux (clé de partition de core.flux). */
    public int annee() {
        return dateFlux.getYear();
    }

    private static void exiger(boolean condition, String motif) {
        if (!condition) {
            throw new FluxInvalideException("Flux invalide : " + motif);
        }
    }
}
