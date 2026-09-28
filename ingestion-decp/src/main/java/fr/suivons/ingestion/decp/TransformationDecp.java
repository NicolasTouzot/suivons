package fr.suivons.ingestion.decp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import fr.suivons.domain.Canal;
import fr.suivons.domain.FluxNormalise;
import fr.suivons.domain.NatureMontant;
import fr.suivons.domain.Payeur;
import fr.suivons.domain.QualiteMontant;
import fr.suivons.domain.Siren;
import fr.suivons.domain.TypePayeur;
import fr.suivons.ingestion.core.source.Transformation;

/**
 * Enregistrement DECP fusionné → flux du canal `MARCHE` (SPEC.md §6.4) :
 * groupement → `PARTAGE`, accord-cadre → `PLAFOND`, sinon `FERME` ; montant arrondi à l'euro ;
 * `ABERRANT` si ≤ 1 € ou au-delà du seuil. Seul un SIRET valide donne un SIREN source.
 */
public class TransformationDecp {

    static final String TYPE_SIRET = "SIRET";

    private final DecpProperties reglages;

    public TransformationDecp(DecpProperties reglages) {
        this.reglages = reglages;
    }

    public Transformation transformer(MarcheTitulaire marche) {
        LocalDate date;
        try {
            date = LocalDate.parse(marche.dateNotification());
        } catch (DateTimeParseException e) {
            return Transformation.rejet(marche.sourceRecordId(), "date de notification absente ou invalide");
        }
        long montant;
        try {
            montant = new BigDecimal(marche.montant()).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return Transformation.rejet(marche.sourceRecordId(), "montant absent ou invalide");
        }
        NatureMontant nature = marche.groupement() ? NatureMontant.PARTAGE
                : marche.accordCadre() ? NatureMontant.PLAFOND
                : NatureMontant.FERME;
        Optional<Long> ferme = nature == NatureMontant.FERME ? Optional.of(montant) : Optional.empty();
        QualiteMontant qualite = montant <= 1 || montant > reglages.seuilAberrant()
                ? QualiteMontant.ABERRANT
                : QualiteMontant.OK;
        Optional<Siren> siren = TYPE_SIRET.equals(marche.titulaireType())
                ? Siren.lire(marche.titulaireId())
                : Optional.empty();
        return Transformation.flux(new FluxNormalise(
                marche.sourceRecordId(),
                lienSource(marche),
                Canal.MARCHE,
                siren,
                Optional.empty(),
                Optional.of(new Payeur(marche.acheteurId(), Optional.empty(), TypePayeur.AUTRE)),
                Optional.of(marche.objet()).filter(o -> !o.isBlank()),
                date,
                ferme,
                Optional.of(montant),
                nature,
                qualite));
    }

    /** Page du jeu filtrée sur le marché (acheteur et identifiant). */
    String lienSource(MarcheTitulaire marche) {
        String recherche = "id:\"" + marche.id() + "\" AND acheteur_id:\"" + marche.acheteurId() + "\"";
        return reglages.pageJeu() + "?q=" + URLEncoder.encode(recherche, StandardCharsets.UTF_8);
    }
}
