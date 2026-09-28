package fr.suivons.api.entreprises;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.RestController;

import fr.suivons.contract.api.EntreprisesApi;
import fr.suivons.contract.model.EntrepriseSynthese;
import fr.suivons.contract.model.PageFlux;
import fr.suivons.domain.IdentifiantInvalideException;
import fr.suivons.domain.Siren;

/** F2 et F3 (SPEC.md §8) : synthèse et flux d'une entreprise, en lecture seule. */
@RestController
public class EntreprisesController implements EntreprisesApi {

    static final String AUCUN_FLUX = "Aucun flux tracé pour ce SIREN : l'entreprise n'est bénéficiaire d'aucun "
            + "flux des sources observées.";

    private final LectureEntreprises lecture;

    public EntreprisesController(LectureEntreprises lecture) {
        this.lecture = lecture;
    }

    @Override
    public ResponseEntity<EntrepriseSynthese> getEntreprise(String siren) {
        return lecture.synthese(lire(siren))
                .map(ResponseEntity::ok)
                .orElseThrow(EntreprisesController::introuvable);
    }

    @Override
    public ResponseEntity<PageFlux> getFluxEntreprise(String siren, Integer page, Integer size) {
        return lecture.flux(lire(siren), page, size)
                .map(ResponseEntity::ok)
                .orElseThrow(EntreprisesController::introuvable);
    }

    private static Siren lire(String siren) {
        try {
            return new Siren(siren);
        } catch (IdentifiantInvalideException e) {
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST,
                    ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "SIREN invalide : " + siren), e);
        }
    }

    private static ErrorResponseException introuvable() {
        return new ErrorResponseException(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, AUCUN_FLUX), null);
    }
}
