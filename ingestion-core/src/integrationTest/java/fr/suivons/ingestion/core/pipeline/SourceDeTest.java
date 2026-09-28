package fr.suivons.ingestion.core.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import fr.suivons.domain.Canal;
import fr.suivons.domain.FluxNormalise;
import fr.suivons.domain.NatureMontant;
import fr.suivons.domain.Payeur;
import fr.suivons.domain.QualiteMontant;
import fr.suivons.domain.Siren;
import fr.suivons.domain.TypePayeur;
import fr.suivons.ingestion.core.source.ContexteExtraction;
import fr.suivons.ingestion.core.source.EnregistrementSource;
import fr.suivons.ingestion.core.source.FichierSource;
import fr.suivons.ingestion.core.source.SourceFlux;
import fr.suivons.ingestion.core.source.Transformation;

/** Source fictive : lit une version de /fixtures/ingestion-core (`id;siren;montant;date;nature`). */
class SourceDeTest implements SourceFlux {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    volatile String version = "version-1.csv";
    volatile boolean enPanne;

    @Override
    public String code() {
        return "TEST";
    }

    @Override
    public String tableBrute() {
        return "raw.test_record";
    }

    @Override
    public FichierSource extraire(ContexteExtraction contexte) throws IOException {
        Files.createDirectories(contexte.repertoireCache());
        Path cible = contexte.repertoireCache().resolve("source.csv");
        try (InputStream fixture = getClass().getResourceAsStream("/ingestion-core/" + version)) {
            Files.copy(fixture, cible, StandardCopyOption.REPLACE_EXISTING);
        }
        return new FichierSource(cible, version);
    }

    @Override
    public Stream<EnregistrementSource> lire(FichierSource fichier) throws IOException {
        return Files.lines(fichier.chemin())
                .filter(ligne -> !ligne.isBlank())
                .map(ligne -> {
                    String[] c = ligne.split(";", -1);
                    String json = "{\"id\":\"%s\",\"siren\":\"%s\",\"montant\":%s,\"date\":\"%s\",\"nature\":\"%s\"}"
                            .formatted(c[0], c[1], c[2], c[3], c[4]);
                    return new EnregistrementSource(c[0], json);
                });
    }

    @Override
    public Transformation transformer(EnregistrementSource enregistrement) {
        if (enPanne) {
            throw new IllegalStateException("Panne simulée de la transformation");
        }
        JsonNode json = JSON.readTree(enregistrement.payload());
        long montant = json.get("montant").asLong();
        NatureMontant nature = NatureMontant.valueOf(json.get("nature").asString());
        return Transformation.flux(new FluxNormalise(
                enregistrement.sourceRecordId(),
                "https://source.test/" + enregistrement.sourceRecordId(),
                Canal.MARCHE,
                Siren.lire(json.get("siren").asString()),
                Optional.of("Bénéficiaire " + enregistrement.sourceRecordId()),
                Optional.of(new Payeur("13000501000033", Optional.of("Acheteur de test"), TypePayeur.ETAT)),
                Optional.of("Objet " + enregistrement.sourceRecordId()),
                LocalDate.parse(json.get("date").asString()),
                nature == NatureMontant.FERME ? Optional.of(montant) : Optional.empty(),
                Optional.of(montant),
                nature,
                QualiteMontant.OK));
    }
}
