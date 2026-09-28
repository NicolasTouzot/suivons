package fr.suivons.referentiel;

import java.time.Duration;
import java.util.function.Supplier;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Enveloppe d'un appel à une API d'appui : limiteur de débit, relances (Retry-After respecté) et disjoncteur.
 * Le disjoncteur est extérieur aux relances : une suite de tentatives compte pour un seul appel.
 */
public final class Protection {

    private final String nom;
    private final RateLimiter limiteur;
    private final Retry relances;
    private final CircuitBreaker disjoncteur;

    public Protection(String nom, int appelsParPeriode, Duration periode, ProtectionProperties reglages) {
        this.nom = nom;
        // Débit lissé (un appel par intervalle) plutôt qu'une rafale en début de période : le fournisseur peut
        // compter sur une fenêtre glissante (constaté avec l'API Sirene : 429 après une rafale de 30 appels)
        this.limiteur = RateLimiter.of(nom, RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(periode.dividedBy(appelsParPeriode))
                .timeoutDuration(reglages.attentePermis())
                .build());
        this.relances = Retry.of(nom, RetryConfig.<Object>custom()
                .maxAttempts(reglages.tentatives())
                .intervalBiFunction(intervalle(reglages))
                .retryOnException(erreur -> relancable(erreur, reglages))
                .build());
        this.disjoncteur = CircuitBreaker.of(nom, CircuitBreakerConfig.custom()
                .failureRateThreshold(reglages.seuilEchecPourcent())
                .minimumNumberOfCalls(reglages.appelsMinimum())
                .slidingWindowSize(Math.max(reglages.appelsMinimum(), 10))
                .waitDurationInOpenState(reglages.dureeOuverture())
                .recordExceptions(ReferentielIndisponibleException.class)
                .ignoreExceptions(AppelRefuseException.class)
                .build());
    }

    public <T> T executer(Supplier<T> appel) {
        Supplier<T> protege = RateLimiter.decorateSupplier(limiteur, appel);
        protege = Retry.decorateSupplier(relances, protege);
        protege = CircuitBreaker.decorateSupplier(disjoncteur, protege);
        try {
            return protege.get();
        } catch (CallNotPermittedException e) {
            throw new ReferentielIndisponibleException(nom + " : disjoncteur ouvert, appel non tenté", e);
        } catch (RequestNotPermitted e) {
            throw new QuotaDepasseException(nom + " : débit maximal atteint", null);
        }
    }

    /** État du disjoncteur, pour la supervision et les tests. */
    public CircuitBreaker.State etatDisjoncteur() {
        return disjoncteur.getState();
    }

    /** Relance les indisponibilités, sauf un refus d'appel ou un quota dont l'attente dépasse le maximum. */
    private static boolean relancable(Throwable erreur, ProtectionProperties reglages) {
        if (erreur instanceof AppelRefuseException) {
            return false;
        }
        if (erreur instanceof QuotaDepasseException quota) {
            return quota.attente().map(attente -> attente.compareTo(reglages.attenteMaximale()) <= 0).orElse(true);
        }
        return erreur instanceof ReferentielIndisponibleException;
    }

    private static IntervalBiFunction<Object> intervalle(ProtectionProperties reglages) {
        return (tentative, resultat) -> {
            long exponentiel = reglages.attenteInitiale().toMillis() * (1L << Math.max(0, tentative - 1));
            long attente = Math.min(exponentiel, reglages.attenteMaximale().toMillis());
            if (resultat != null && resultat.isLeft()
                    && resultat.getLeft() instanceof QuotaDepasseException quota
                    && quota.attente().isPresent()) {
                attente = Math.min(quota.attente().get().toMillis(), reglages.attenteMaximale().toMillis());
            }
            return attente;
        };
    }

    /** Intervalle minimal entre deux appels (débit lissé). */
    Duration intervalleEntreAppels() {
        return limiteur.getRateLimiterConfig().getLimitRefreshPeriod();
    }
}
