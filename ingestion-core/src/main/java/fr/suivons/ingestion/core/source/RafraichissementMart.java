package fr.suivons.ingestion.core.source;

/** Rafraîchissement des agrégats servis par l'API après un run réussi (mart, lot 3). Jamais après un échec. */
@FunctionalInterface
public interface RafraichissementMart {

    RafraichissementMart AUCUN = () -> { };

    void rafraichir();
}
