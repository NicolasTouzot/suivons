package fr.suivons.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Numéro SIREN d'une unité légale : 9 chiffres, clé de Luhn valide.
 * Les sources le fournissent sous des formes variées (espaces, espaces insécables, SIRET complet) :
 * {@link #lire(String)} les normalise.
 */
public record Siren(String valeur) {

    private static final Pattern NEUF_CHIFFRES = Pattern.compile("\\d{9}");
    private static final Pattern QUATORZE_CHIFFRES = Pattern.compile("\\d{14}");
    private static final String SIREN_LA_POSTE = "356000000";
    private static final Pattern SEPARATEURS = Pattern.compile("[\\s\\u00A0\\u202F.-]");

    public Siren {
        if (valeur == null || !NEUF_CHIFFRES.matcher(valeur).matches() || !cleDeLuhnValide(valeur)) {
            throw new IdentifiantInvalideException("SIREN invalide : " + valeur);
        }
    }

    /** SIREN lu depuis un identifiant source (SIREN ou SIRET, séparateurs tolérés) ; vide s'il est invalide. */
    public static Optional<Siren> lire(String identifiant) {
        if (identifiant == null) {
            return Optional.empty();
        }
        String chiffres = SEPARATEURS.matcher(identifiant).replaceAll("");
        String candidat;
        if (NEUF_CHIFFRES.matcher(chiffres).matches()) {
            candidat = chiffres;
        } else if (QUATORZE_CHIFFRES.matcher(chiffres).matches() && siretValide(chiffres)) {
            candidat = chiffres.substring(0, 9);
        } else {
            return Optional.empty();
        }
        return cleDeLuhnValide(candidat) ? Optional.of(new Siren(candidat)) : Optional.empty();
    }

    /**
     * Clé d'un SIRET : Luhn, sauf pour les établissements de La Poste (SIREN 356000000), trop nombreux pour la clé
     * de Luhn, dont la somme des chiffres est un multiple de 5 (règle de l'INSEE).
     */
    static boolean siretValide(String siret) {
        if (siret.startsWith(SIREN_LA_POSTE)) {
            return siret.chars().map(c -> c - '0').sum() % 5 == 0;
        }
        return cleDeLuhnValide(siret);
    }

    static boolean cleDeLuhnValide(String chiffres) {
        int somme = 0;
        for (int i = 0; i < chiffres.length(); i++) {
            int chiffre = chiffres.charAt(chiffres.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                chiffre *= 2;
                if (chiffre > 9) {
                    chiffre -= 9;
                }
            }
            somme += chiffre;
        }
        return somme % 10 == 0;
    }

    @Override
    public String toString() {
        return valeur;
    }
}
