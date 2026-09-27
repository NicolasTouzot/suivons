package fr.suivons.referentiel;

/** L'API d'appui refuse la requête (400, 401, 403…) : erreur de configuration ou d'appel, jamais relancée. */
public class AppelRefuseException extends ReferentielIndisponibleException {

    private static final long serialVersionUID = 1L;

    public AppelRefuseException(String message) {
        super(message);
    }
}
