package fr.suivons.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Conventions transverses du contrat (SPEC.md §8, §9.5), vérifiées sur openapi.yaml. */
class ContractConventionsTest {

    private static final Path SPEC = Path.of("openapi.yaml");
    private static final List<String> HTTP_METHODS =
            List.of("get", "put", "post", "delete", "patch", "head", "options");

    static String text;
    static Map<String, Object> contract;

    @BeforeAll
    static void load() throws IOException {
        text = Files.readString(SPEC);
        contract = new Yaml().load(text);
    }

    @Test
    void exposeLaBaseApiV1() {
        List<Map<String, Object>> servers = list(contract.get("servers"));
        assertThat(servers).extracting(s -> s.get("url")).containsExactly("/api/v1");
    }

    @Test
    void decritLeFormatProblemDetailsRfc9457() {
        Map<String, Object> problem = map(map(map(contract.get("components")).get("schemas")).get("Problem"));
        assertThat(map(problem.get("properties")))
                .containsOnlyKeys("type", "title", "status", "detail", "instance");
        assertThat(problem.get("additionalProperties")).isEqualTo(true);
    }

    @Test
    void toutesLesErreursSontAuFormatProblem() {
        Map<String, Object> sharedResponses = map(map(contract.get("components")).get("responses"));
        sharedResponses.values().forEach(r -> assertProblemJson(map(r)));

        Map<String, Object> paths = map(contract.get("paths"));
        paths.values().forEach(item -> map(item).forEach((method, operation) -> {
            if (!HTTP_METHODS.contains(method)) {
                return;
            }
            map(map(operation).get("responses")).forEach((status, response) -> {
                if (status.startsWith("4") || status.startsWith("5")) {
                    Map<String, Object> r = map(response);
                    assertThat(r.containsKey("$ref") || isProblemJson(r))
                            .as("réponse %s %s au format problem+json", method, status)
                            .isTrue();
                }
            });
        }));
    }

    @Test
    void nEmploieQueDesTermesNeutres() {
        assertThat(text.toLowerCase(Locale.ROOT)).doesNotContain("fraude", "corruption", "suspect");
    }

    private static void assertProblemJson(Map<String, Object> response) {
        assertThat(isProblemJson(response)).as("réponse %s", response.get("description")).isTrue();
    }

    private static boolean isProblemJson(Map<String, Object> response) {
        Map<String, Object> content = map(response.get("content"));
        return content != null
                && content.containsKey("application/problem+json")
                && "#/components/schemas/Problem".equals(
                        map(map(content.get("application/problem+json")).get("schema")).get("$ref"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
