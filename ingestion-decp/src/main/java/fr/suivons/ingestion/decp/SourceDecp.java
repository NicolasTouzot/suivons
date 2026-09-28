package fr.suivons.ingestion.decp;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
 * Source DECP, canal `MARCHE` (SPEC.md §4). Extraction : export JSON du jeu `decp-2022-marches-valides` filtré sur
 * un mois de notification ; lecture : fusion des lignes en un enregistrement par couple (marché, titulaire).
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
        verifierIndexationComplete(contexte.repertoireCache());
        Path fichier = contexte.repertoireCache().resolve("decp-2022-" + reglages.mois() + ".json");
        return new FichierSource(telechargeur.telecharger(urlExport(), fichier), reglages.mois().toString());
    }

    /**
     * Pendant une réindexation chez le producteur, l'API n'expose qu'une partie du jeu : un export serait incomplet.
     * Le nombre d'enregistrements interrogeables doit égaler celui annoncé par les métadonnées du jeu.
     */
    void verifierIndexationComplete(Path repertoireCache) throws IOException {
        long annonces = lireNombre(URI.create(reglages.jeu()), repertoireCache.resolve("metadonnees.json"),
                "/metas/default/records_count");
        long indexes = lireNombre(URI.create(reglages.jeu() + "/records?limit=0"),
                repertoireCache.resolve("decompte.json"), "/total_count");
        if (annonces != indexes) {
            throw new IOException("Jeu DECP en cours de mise à jour chez le producteur : " + indexes
                    + " enregistrements interrogeables sur " + annonces + " annoncés ; export reporté");
        }
    }

    private long lireNombre(URI url, Path fichier, String chemin) throws IOException {
        try {
            JsonNode valeur = json.readTree(telechargeur.telecharger(url, fichier).toFile()).at(chemin);
            if (!valeur.isNumber()) {
                throw new IOException("Réponse inattendue de " + url + " : " + chemin + " absent");
            }
            return valeur.longValue();
        } catch (JacksonException e) {
            throw new IOException("Réponse illisible de " + url, e);
        }
    }

    /** Export JSON des marchés notifiés pendant le mois configuré. */
    URI urlExport() {
        String filtre = "datenotification>=date'" + reglages.mois().atDay(1) + "' and datenotification<date'"
                + reglages.mois().plusMonths(1).atDay(1) + "'";
        return URI.create(reglages.jeu() + "/exports/json?where=" + URLEncoder.encode(filtre, StandardCharsets.UTF_8));
    }

    @Override
    public Stream<EnregistrementSource> lire(FichierSource fichier) throws IOException {
        List<LigneDecp> lignes;
        try {
            lignes = Arrays.asList(json.readValue(fichier.chemin().toFile(), LigneDecp[].class));
        } catch (JacksonException e) {
            throw new IOException("Export DECP illisible : " + fichier.chemin(), e);
        }
        return FusionDecp.fusionner(lignes).stream()
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
