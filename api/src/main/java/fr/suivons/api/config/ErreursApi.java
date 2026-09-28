package fr.suivons.api.config;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Erreurs de l'API au format RFC 9457. Les contraintes du contrat (paramètres des interfaces générées) sont
 * vérifiées par validation de méthode : une violation est une requête invalide (400), jamais une erreur serveur.
 */
@RestControllerAdvice
public class ErreursApi {

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail requeteInvalide(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(ErreursApi::decrire)
                .sorted()
                .collect(Collectors.joining(" ; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Paramètre invalide : " + detail);
    }

    private static String decrire(ConstraintViolation<?> violation) {
        String chemin = violation.getPropertyPath().toString();
        String parametre = chemin.substring(chemin.lastIndexOf('.') + 1);
        return parametre + " " + violation.getMessage();
    }
}
