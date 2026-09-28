package fr.suivons.ingestion.decp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Fusion des lignes DECP en un enregistrement par couple (marché, titulaire), version minimale du lot 1 bis
 * (SPEC.md §6.4 ; règles complètes et calibrage au lot 2) :
 * <ul>
 * <li>seules les lignes de la dernière date de publication sont retenues (même marché republié, plusieurs
 * plateformes) ;</li>
 * <li>montant : celui de la modification la plus récente qui en porte un, sinon le plus élevé des montants publiés
 * (lots d'un même titulaire publiés sous un seul identifiant : borne prudente, jamais une somme) ;</li>
 * <li>les lignes de sous-traitance n'apportent rien (même montant répété) ;</li>
 * <li>groupement si une ligne compte plusieurs co-titulaires ; accord-cadre si une ligne l'indique.</li>
 * </ul>
 */
public final class FusionDecp {

    private static final Comparator<LigneDecp> ORDRE_STABLE = Comparator
            .comparing((LigneDecp l) -> texte(l.plateforme()))
            .thenComparing(l -> l.montant() == null ? BigDecimal.ZERO : l.montant())
            .thenComparing(l -> texte(l.objet()));

    private FusionDecp() {
    }

    /** Enregistrements fusionnés, triés par identifiant source. */
    public static List<MarcheTitulaire> fusionner(List<LigneDecp> lignes) {
        Map<String, List<LigneDecp>> parCle = new TreeMap<>();
        Map<String, LigneDecp.Titulaire> titulaires = new LinkedHashMap<>();
        for (LigneDecp ligne : lignes) {
            for (LigneDecp.Titulaire titulaire : ligne.titulaires()) {
                String cle = ligne.acheteurId() + "|" + ligne.id() + "|" + titulaire.identifiant();
                parCle.computeIfAbsent(cle, c -> new ArrayList<>()).add(ligne);
                titulaires.putIfAbsent(cle, titulaire);
            }
        }
        List<MarcheTitulaire> resultat = new ArrayList<>(parCle.size());
        parCle.forEach((cle, groupe) -> resultat.add(fusionner(groupe, titulaires.get(cle))));
        return resultat;
    }

    private static MarcheTitulaire fusionner(List<LigneDecp> groupe, LigneDecp.Titulaire titulaire) {
        String derniere = groupe.stream().map(l -> texte(l.datePublication())).max(Comparator.naturalOrder())
                .orElse("");
        List<LigneDecp> retenues = groupe.stream()
                .filter(l -> texte(l.datePublication()).equals(derniere))
                .sorted(ORDRE_STABLE)
                .toList();
        LigneDecp reference = retenues.getLast();
        Optional<BigDecimal> modifie = retenues.stream()
                .filter(l -> l.montantModifie().isPresent())
                .max(Comparator.comparing((LigneDecp l) -> l.dateModificationOuVide().orElse(""))
                        .thenComparing(l -> l.modification().orElse("")))
                .flatMap(LigneDecp::montantModifie);
        Optional<BigDecimal> publie = retenues.stream().map(LigneDecp::montant)
                .filter(m -> m != null).max(Comparator.naturalOrder());
        String dateNotification = retenues.stream().map(l -> texte(l.dateNotification()))
                .filter(d -> !d.isEmpty()).min(Comparator.naturalOrder()).orElse("");
        return new MarcheTitulaire(
                reference.acheteurId(),
                reference.id(),
                titulaire.identifiant(),
                titulaire.type(),
                modifie.or(() -> publie).map(BigDecimal::toPlainString).orElse(""),
                dateNotification,
                derniere,
                texte(reference.objet()),
                retenues.stream().anyMatch(LigneDecp::accordCadre),
                retenues.stream().anyMatch(l -> l.titulaires().size() > 1),
                texte(reference.plateforme()),
                groupe.size());
    }

    private static String texte(String valeur) {
        return valeur == null ? "" : valeur.strip();
    }
}
