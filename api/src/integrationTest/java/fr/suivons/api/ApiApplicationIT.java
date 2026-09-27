package fr.suivons.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.yaml.snakeyaml.Yaml;

import fr.suivons.api.config.ApiPathConfig;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ApiApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(System.getProperty("suivons.postgres.image"));

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

    @Test
    void healthEstUpAvecLaBase() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode health = JSON.readTree(response.body());
        assertThat(health.path("status").asString()).isEqualTo("UP");
        assertThat(health.path("components").path("db").path("status").asString()).isEqualTo("UP");
    }

    @Test
    void exposeLesSondesDeVivaciteEtDeDisponibilite() throws Exception {
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
    }

    @Test
    void infoExposeLaVersionDuBuild() throws Exception {
        JsonNode info = JSON.readTree(get("/actuator/info").body());
        assertThat(info.path("build").path("name").asString()).isEqualTo("api");
        assertThat(info.path("build").path("version").asString()).isNotEqualTo("unspecified");
    }

    @Test
    void lesAutresEndpointsActuatorNeSontPasExposes() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
    }

    @Test
    void lesControleursRecoiventLePrefixeDuContrat() throws Exception {
        assertThat(get(ApiPathConfig.BASE_PATH + "/sonde-prefixe").body()).isEqualTo("ok");
        assertThat(get("/sonde-prefixe").statusCode()).isEqualTo(404);
    }

    @Test
    void lePrefixeEstCeluiDuContrat() throws IOException {
        try (InputStream spec = getClass().getResourceAsStream("/openapi/openapi.yaml")) {
            Map<String, Object> contract = new Yaml().load(spec);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> servers = (List<Map<String, Object>>) contract.get("servers");
            assertThat(servers).extracting(s -> s.get("url")).containsExactly(ApiPathConfig.BASE_PATH);
        }
    }

    @Test
    void uneRessourceInconnueRepondAuFormatProblem() throws Exception {
        HttpResponse<String> response = get(ApiPathConfig.BASE_PATH + "/inconnu");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        JsonNode problem = JSON.readTree(response.body());
        assertThat(problem.path("status").asInt()).isEqualTo(404);
        assertThat(problem.path("title").asString()).isNotBlank();
        assertThat(problem.path("instance").asString()).isEqualTo(ApiPathConfig.BASE_PATH + "/inconnu");
    }

    @Test
    void lesLogsSontEnJsonStructure(CapturedOutput output) {
        LoggerFactory.getLogger(ApiApplicationIT.class).info("ligne de controle");

        String line = output.getOut().lines()
                .filter(l -> l.contains("ligne de controle"))
                .findFirst()
                .orElseThrow();
        JsonNode log = JSON.readTree(line);
        assertThat(log.path("message").asString()).isEqualTo("ligne de controle");
        assertThat(log.path("log").path("level").asString()).isEqualTo("INFO");
        assertThat(log.path("service").path("name").asString()).isEqualTo("suivons-api");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
