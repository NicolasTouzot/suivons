package fr.suivons.ingestion.decp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Lecture de l'export Parquet du jeu DECP avec DuckDB (base en mémoire, sans serveur), triée par marché
 * (acheteur, identifiant) : les lignes d'un marché arrivent ensemble et sont fusionnées marché par marché, sans
 * charger le jeu entier en mémoire.
 */
public final class LecteurParquet {

    private static final String COLONNES = String.join(", ",
            "acheteur_id", "id", "objet", "techniques", "CAST(montant AS VARCHAR) AS montant",
            "CAST(datenotification AS VARCHAR) AS datenotification",
            "CAST(datepublicationdonnees AS VARCHAR) AS datepublicationdonnees", "source",
            "titulaire_id_1", "titulaire_typeidentifiant_1", "titulaire_id_2", "titulaire_typeidentifiant_2",
            "titulaire_id_3", "titulaire_typeidentifiant_3", "idmodification", "montantmodification",
            "datenotificationmodificationmodification");

    private LecteurParquet() {
    }

    /** Enregistrements fusionnés (un par couple marché, titulaire), marché par marché ; le flux doit être fermé. */
    public static Stream<MarcheTitulaire> lire(Path fichier) throws IOException {
        Connection connexion;
        ResultSet lignes;
        try {
            connexion = DriverManager.getConnection("jdbc:duckdb:");
            Statement requete = connexion.createStatement();
            requete.execute("SET memory_limit = '1GB'");
            lignes = requete.executeQuery("SELECT " + COLONNES + " FROM read_parquet('"
                    + fichier.toAbsolutePath().toString().replace("'", "''") + "') ORDER BY acheteur_id, id");
        } catch (SQLException e) {
            throw new IOException("Lecture de l'export Parquet impossible : " + fichier, e);
        }
        return StreamSupport.stream(new ParMarche(lignes), false).onClose(() -> {
            try {
                connexion.close();
            } catch (SQLException e) {
                throw new UncheckedIOException(new IOException("Fermeture de DuckDB impossible", e));
            }
        });
    }

    /** Regroupe les lignes consécutives d'un même marché et émet leurs enregistrements fusionnés. */
    private static final class ParMarche extends Spliterators.AbstractSpliterator<MarcheTitulaire> {

        private final ResultSet lignes;
        private final Deque<MarcheTitulaire> prets = new ArrayDeque<>();
        private List<LigneDecp> marche = new ArrayList<>();
        private String cleMarche;
        private boolean termine;

        ParMarche(ResultSet lignes) {
            super(Long.MAX_VALUE, Spliterator.ORDERED | Spliterator.NONNULL);
            this.lignes = lignes;
        }

        @Override
        public boolean tryAdvance(Consumer<? super MarcheTitulaire> action) {
            while (prets.isEmpty() && !termine) {
                avancer();
            }
            if (prets.isEmpty()) {
                return false;
            }
            action.accept(prets.poll());
            return true;
        }

        private void avancer() {
            try {
                if (!lignes.next()) {
                    termine = true;
                    emettre();
                    return;
                }
                LigneDecp ligne = ligne(lignes);
                String cle = ligne.acheteurId() + "|" + ligne.id();
                if (!cle.equals(cleMarche)) {
                    emettre();
                    cleMarche = cle;
                }
                marche.add(ligne);
            } catch (SQLException e) {
                throw new UncheckedIOException(new IOException("Lecture de l'export Parquet interrompue", e));
            }
        }

        private void emettre() {
            if (!marche.isEmpty()) {
                prets.addAll(FusionDecp.fusionner(marche));
                marche = new ArrayList<>();
            }
        }

        private static LigneDecp ligne(ResultSet r) throws SQLException {
            String montant = r.getString("montant");
            return new LigneDecp(r.getString("acheteur_id"), r.getString("id"), r.getString("objet"),
                    r.getString("techniques"), montant == null ? null : new BigDecimal(montant),
                    r.getString("datenotification"), r.getString("datepublicationdonnees"), r.getString("source"),
                    r.getString("titulaire_id_1"), r.getString("titulaire_typeidentifiant_1"),
                    r.getString("titulaire_id_2"), r.getString("titulaire_typeidentifiant_2"),
                    r.getString("titulaire_id_3"), r.getString("titulaire_typeidentifiant_3"),
                    r.getString("idmodification"), r.getString("montantmodification"),
                    r.getString("datenotificationmodificationmodification"));
        }
    }
}
