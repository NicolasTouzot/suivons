package fr.suivons.ingestion.decp;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import fr.suivons.domain.FluxNormalise;
import fr.suivons.domain.Siren;
import fr.suivons.ingestion.core.source.Rattachement;
import fr.suivons.ingestion.core.source.Rattacheur;
import fr.suivons.reconciliation.referentiel.ReferentielMinimal;

/**
 * Rattachement par le SIREN fourni par la source (SPEC.md §6.5, étape 1) : le SIREN doit exister dans le
 * référentiel ou dans Sirene (ajouté alors au référentiel), sinon le flux reste `NON_RESOLU`.
 * <p>
 * Les SIREN du fichier sont lus dans Sirene en amont, par lots de 1 000 ({@link #preparer}) ; ceux que l'INSEE ne
 * connaît pas sont retenus pour la durée du processus (une ingestion par lancement) et ne sont plus redemandés.
 */
public class RattacheurSiret implements Rattacheur {

    private final ReferentielMinimal referentiel;
    private final Set<Siren> introuvables = ConcurrentHashMap.newKeySet();

    public RattacheurSiret(ReferentielMinimal referentiel) {
        this.referentiel = referentiel;
    }

    @Override
    public void preparer(List<Siren> sirens) {
        verifier(sirens);
    }

    @Override
    public Rattachement rattacher(FluxNormalise flux) {
        return rattacherLot(List.of(flux)).getFirst();
    }

    @Override
    public List<Rattachement> rattacherLot(List<FluxNormalise> lot) {
        Set<Siren> presents = verifier(lot.stream()
                .map(FluxNormalise::sirenSource)
                .flatMap(Optional::stream)
                .distinct()
                .toList());
        return lot.stream()
                .map(flux -> flux.sirenSource()
                        .filter(presents::contains)
                        .map(Rattachement::sirenSource)
                        .orElseGet(Rattachement::nonResolu))
                .toList();
    }

    /** SIREN présents dans le référentiel, après ajout de ceux que Sirene connaît. */
    private Set<Siren> verifier(List<Siren> sirens) {
        List<Siren> aVerifier = sirens.stream().filter(s -> !introuvables.contains(s)).toList();
        Set<Siren> presents = referentiel.garantir(aVerifier);
        aVerifier.stream().filter(s -> !presents.contains(s)).forEach(introuvables::add);
        return presents;
    }
}
