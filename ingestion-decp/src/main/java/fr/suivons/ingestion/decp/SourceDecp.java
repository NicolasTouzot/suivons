package fr.suivons.ingestion.decp;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.stream.Stream;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import fr.suivons.ingestion.core.pipeline.Telechargeur;
import fr.suivons.ingestion.core.source.ContexteExtraction;
import fr.suivons.ingestion.core.source.EnregistrementSource;
import fr.suivons.ingestion.core.source.FichierSource;
import fr.suivons.ingestion.core.source.SourceFlux;
import fr.suivons.ingestion.core.source.Transformation;

/**
 * Source DECP, canal `MARCHE` (SPEC.md §4). Extraction : export Parquet complet du jeu `decp-2022-marches-valides`
 * (reporté si le producteur réindexe le jeu) ; lecture : fusion des lignes en un enregistrement par couple
 * (marché, titulaire), marché par marché.
 */
public class SourceDecp implements SourceFlux {

    public static final String CODE = "DECP";

    private final DecpProperties reglages;
    private final Telechargeur telechargeur;
    private final TransformationDecp transformation;
    private final JsonMapper json = JsonMapper.builder().build();

    public SourceDecp(DecpProperties reglages, Telechargeur telechargeur) {
        this.reglages = reglages;
        this.telechargeur = telechargeur;
        this.transformation = new TransformationDecp(reglages);
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String tableBrute() {
        return "raw.decp_record";
    }

    @Override
    public FichierSource extraire(ContexteExtraction contexte) throws IOException {
        String version = verifierIndexationComplete(contexte.repertoireCache());
        Path fichier = contexte.repertoireCache().resolve("decp-2022.parquet");
        return new FichierSource(telechargeur.telecharger(URI.create(reglages.jeu() + "/exports/parquet"), fichier),
                version);
    }

    /**
     * Pendant une réindexation chez le producteur, l'API n'expose qu'une partie du jeu : un export serait incomplet.
     * Le nombre d'enregistrements interrogeables doit égaler celui annoncé par les métadonnées du jeu.
     *
     * @return date de dernière modification du jeu, version de la source
     */
    String verifierIndexationComplete(Path repertoireCache) throws IOException {
        JsonNode metadonnees = lire(URI.create(reglages.jeu()), repertoireCache.resolve("metadonnees.json"))
                .at("/metas/default");
        long annonces = nombre(metadonnees.at("/records_count"), "records_count");
        long indexes = nombre(lire(URI.create(reglages.jeu() + "/records?limit=0"),
                repertoireCache.resolve("decompte.json")).at("/total_count"), "total_count");
        if (annonces != indexes) {
            throw new IOException("Jeu DECP en cours de mise à jour chez le producteur : " + indexes
                    + " enregistrements interrogeables sur " + annonces + " annoncés ; export reporté");
        }
        return metadonnees.at("/modified").isString() ? metadonnees.at("/modified").asString()
                : String.valueOf(annonces);
    }

    private JsonNode lire(URI url, Path fichier) throws IOException {
        try {
            return json.readTree(telechargeur.telecharger(url, fichier).toFile());
        } catch (JacksonException e) {
            throw new IOException("Réponse illisible de " + url, e);
        }
    }

    private static long nombre(JsonNode valeur, String champ) throws IOException {
        if (!valeur.isNumber()) {
            throw new IOException("Réponse inattendue du portail DECP : " + champ + " absent");
        }
        return valeur.longValue();
    }

    @Override
    public Stream<EnregistrementSource> lire(FichierSource fichier) throws IOException {
        return LecteurParquet.lire(fichier.chemin())
                .map(marche -> new EnregistrementSource(marche.sourceRecordId(), json.writeValueAsString(marche)));
    }

    @Override
    public Transformation transformer(EnregistrementSource enregistrement) {
        MarcheTitulaire marche;
        try {
            marche = json.readValue(enregistrement.payload(), MarcheTitulaire.class);
        } catch (JacksonException e) {
            return Transformation.rejet(enregistrement.sourceRecordId(), "enregistrement brut illisible");
        }
        return transformation.transformer(marche);
    }
}
