package fr.suivons.ingestion.decp;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import fr.suivons.domain.FluxNormalise;
import fr.suivons.domain.Siren;
import fr.suivons.ingestion.core.source.Rattachement;
import fr.suivons.ingestion.core.source.Rattacheur;
import fr.suivons.reconciliation.referentiel.ReferentielMinimal;

/**
 * Rattachement par le SIREN fourni par la source (SPEC.md §6.5, étape 1), seule étape du lot 1 bis : le SIREN
 * doit exister dans le référentiel ou dans Sirene (ajouté alors au référentiel), sinon le flux reste `NON_RESOLU`.
 */
public class RattacheurSiret implements Rattacheur {

    private final ReferentielMinimal referentiel;

    public RattacheurSiret(ReferentielMinimal referentiel) {
        this.referentiel = referentiel;
    }

    @Override
    public Rattachement rattacher(FluxNormalise flux) {
        return rattacherLot(List.of(flux)).getFirst();
    }

    @Override
    public List<Rattachement> rattacherLot(List<FluxNormalise> lot) {
        Set<Siren> presents = referentiel.garantir(lot.stream()
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
}
