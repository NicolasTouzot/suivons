package fr.suivons.reconciliation.referentiel;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import fr.suivons.domain.Siren;
import fr.suivons.referentiel.sirene.Siege;
import fr.suivons.referentiel.sirene.SireneClient;
import fr.suivons.referentiel.sirene.UniteLegale;

/**
 * Référentiel minimal des bénéficiaires (SPEC.md §6.3, §6.5) : lecture groupée dans l'API Sirene et ajout des
 * entreprises rattachées. Les erreurs de l'API (quota, indisponibilité) sont propagées : l'appelant échoue plutôt
 * que de conclure à tort qu'un SIREN n'existe pas.
 */
public class ReferentielMinimal {

    private final SireneClient sirene;
    private final DepotEntreprises entreprises;

    public ReferentielMinimal(SireneClient sirene, DepotEntreprises entreprises) {
        this.sirene = sirene;
        this.entreprises = entreprises;
    }

    /** Identité minimale des SIREN demandés, lue dans Sirene (unités légales et sièges) ; les inconnus sont absents. */
    public List<EntrepriseReferentiel> lireDansSirene(Collection<Siren> sirens) {
        Set<Siren> demandes = Set.copyOf(sirens);
        if (demandes.isEmpty()) {
            return List.of();
        }
        List<UniteLegale> unites = sirene.unitesLegales(demandes).stream()
                .filter(u -> demandes.contains(u.siren()))
                .toList();
        Map<Siren, Siege> sieges = unites.isEmpty() ? Map.of()
                : sirene.sieges(unites.stream().map(UniteLegale::siren).toList()).stream()
                        .collect(Collectors.toMap(Siege::siren, Function.identity(), (a, b) -> a));
        return unites.stream()
                .map(u -> EntrepriseReferentiel.depuis(u, Optional.ofNullable(sieges.get(u.siren()))))
                .toList();
    }

    /**
     * Garantit la présence des SIREN dans le référentiel : ceux qui manquent sont lus dans Sirene et ajoutés.
     * Renvoie les SIREN présents au retour ; un SIREN inconnu de l'INSEE n'y figure pas.
     */
    public Set<Siren> garantir(Collection<Siren> sirens) {
        Set<Siren> presents = new HashSet<>(entreprises.existants(sirens));
        List<Siren> manquants = sirens.stream().distinct().filter(s -> !presents.contains(s)).toList();
        List<EntrepriseReferentiel> lues = lireDansSirene(manquants);
        entreprises.ajouter(lues, OffsetDateTime.now());
        lues.forEach(e -> presents.add(e.siren()));
        return presents;
    }
}
