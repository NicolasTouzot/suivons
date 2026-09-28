package fr.suivons.ingestion.sirene.naf;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Lecture des sous-classes d'un fichier NAF de l'INSEE (.xls ou .xlsx) : lignes dont la colonne A porte un code
 * de sous-classe, libellé en colonne B. Les lignes de titre et les niveaux supérieurs sont ignorés.
 */
public final class LecteurNaf {

    private static final Pattern SOUS_CLASSE = Pattern.compile("\\d{2}\\.\\d{2}[A-Z]");

    private LecteurNaf() {
    }

    public static List<CodeNaf> lire(Path fichier, String feuille) throws IOException {
        DataFormatter texte = new DataFormatter();
        List<CodeNaf> codes = new ArrayList<>();
        try (Workbook classeur = WorkbookFactory.create(fichier.toFile(), null, true)) {
            Sheet sous = classeur.getSheet(feuille);
            if (sous == null) {
                throw new IOException("Feuille « " + feuille + " » absente de " + fichier.getFileName());
            }
            for (Row ligne : sous) {
                String code = texte.formatCellValue(ligne.getCell(0)).strip();
                if (!SOUS_CLASSE.matcher(code).matches()) {
                    continue;
                }
                Cell cellule = ligne.getCell(1);
                String libelle = texte.formatCellValue(cellule).strip();
                if (libelle.isEmpty()) {
                    throw new IOException("Sous-classe " + code + " sans libellé dans " + fichier.getFileName());
                }
                codes.add(new CodeNaf(code, libelle));
            }
        }
        if (codes.isEmpty()) {
            throw new IOException("Aucune sous-classe NAF dans la feuille « " + feuille + " » de "
                    + fichier.getFileName());
        }
        return codes;
    }
}
