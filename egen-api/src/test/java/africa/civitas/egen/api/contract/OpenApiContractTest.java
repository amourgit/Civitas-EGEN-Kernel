package africa.civitas.egen.api.contract;

import africa.civitas.egen.api.rest.ServiceResource;
import africa.civitas.egen.api.security.IdempotencyRequired;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Niveau 1 (docs 17). {@code egen-contracts/openapi/egen-control-api.yaml} decrit la surface REST livree
 * (docs 13, 16). Ce test rapproche le fichier du code : une route ajoutee, retiree ou deplacee sans mise a
 * jour du contrat — ou l'inverse — fait echouer la CI, comme pour le contrat de resilience partage (12).
 */
class OpenApiContractTest {

    private static final String SPEC = "/openapi/egen-control-api.yaml";
    private static final String IDEMPOTENCY_REF = "#/components/parameters/IdempotencyKey";
    private static final Set<String> VERBS = Set.of("get", "post", "put", "delete");

    private static JsonNode spec;

    @BeforeAll
    static void loadSpec() throws IOException {
        try (InputStream in = OpenApiContractTest.class.getResourceAsStream(SPEC)) {
            assertNotNull(in, "le contrat doit etre sur le classpath de test : " + SPEC);
            spec = new ObjectMapper(new YAMLFactory()).readTree(in);
        }
    }

    // ------------------------------------------------------------------ code

    /** (verbe, chemin normalise) -> la methode de ressource ; chemin normalise : {@code {x}} devient {@code {}}. */
    private static Map<String, Method> routesOfTheCode() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of(
                ServiceResource.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        java.nio.file.Path dir = root.resolve("africa/civitas/egen/api/rest");
        Map<String, Method> routes = new java.util.TreeMap<>();
        try (Stream<java.nio.file.Path> files = Files.list(dir)) {
            for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".class") || name.contains("$")) {
                    continue;
                }
                Class<?> type = Class.forName("africa.civitas.egen.api.rest." + name.substring(0, name.length() - 6));
                Path classPath = type.getAnnotation(Path.class);
                if (classPath == null) {
                    continue;
                }
                for (Method method : type.getDeclaredMethods()) {
                    String verb = verbOf(method);
                    if (verb == null) {
                        continue;
                    }
                    Path methodPath = method.getAnnotation(Path.class);
                    String path = normalize(classPath.value() + "/" + (methodPath == null ? "" : methodPath.value()));
                    routes.put(verb + " " + path, method);
                }
            }
        }
        return routes;
    }

    private static String verbOf(Method method) {
        if (method.isAnnotationPresent(GET.class)) {
            return "GET";
        }
        if (method.isAnnotationPresent(POST.class)) {
            return "POST";
        }
        if (method.isAnnotationPresent(PUT.class)) {
            return "PUT";
        }
        if (method.isAnnotationPresent(DELETE.class)) {
            return "DELETE";
        }
        return null;
    }

    private static String normalize(String path) {
        String result = path.replaceAll("/+", "/").replaceAll("\\{[^}/]+}", "{}");
        return result.length() > 1 && result.endsWith("/") ? result.substring(0, result.length() - 1) : result;
    }

    // ------------------------------------------------------------------ contrat

    /** (verbe, chemin normalise) -> l'operation du contrat. */
    private static Map<String, JsonNode> operationsOfTheContract() {
        Map<String, JsonNode> operations = new java.util.TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> paths = spec.get("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> path = paths.next();
            Iterator<Map.Entry<String, JsonNode>> verbs = path.getValue().fields();
            while (verbs.hasNext()) {
                Map.Entry<String, JsonNode> verb = verbs.next();
                if (VERBS.contains(verb.getKey())) {
                    operations.put(verb.getKey().toUpperCase() + " " + normalize(path.getKey()), verb.getValue());
                }
            }
        }
        return operations;
    }

    // ------------------------------------------------------------------ tests

    @Test
    void everyRestRouteIsInTheContractAndEveryOperationOfTheContractIsARoute() throws Exception {
        TreeSet<String> code = new TreeSet<>(routesOfTheCode().keySet());
        TreeSet<String> contract = new TreeSet<>(operationsOfTheContract().keySet());

        TreeSet<String> missingFromContract = new TreeSet<>(code);
        missingFromContract.removeAll(contract);
        TreeSet<String> missingFromCode = new TreeSet<>(contract);
        missingFromCode.removeAll(code);

        assertTrue(missingFromContract.isEmpty() && missingFromCode.isEmpty(),
                "routes du code absentes du contrat : " + missingFromContract
                        + " ; operations du contrat sans route : " + missingFromCode);
    }

    @Test
    void theMutationsThatRequireAnIdempotencyKeyAreTheSameInCodeAndInContract() throws Exception {
        TreeSet<String> code = new TreeSet<>();
        for (Map.Entry<String, Method> route : routesOfTheCode().entrySet()) {
            if (route.getValue().isAnnotationPresent(IdempotencyRequired.class)) {
                code.add(route.getKey());
            }
        }
        TreeSet<String> contract = new TreeSet<>();
        for (Map.Entry<String, JsonNode> operation : operationsOfTheContract().entrySet()) {
            for (JsonNode parameter : operation.getValue().path("parameters")) {
                if (IDEMPOTENCY_REF.equals(parameter.path("$ref").asText())) {
                    contract.add(operation.getKey());
                }
            }
        }

        assertEquals(code, contract, "Idempotency-Key : le code (@IdempotencyRequired) et le contrat divergent");
    }

    @Test
    void everyMutationAnswersWith202BecauseReconciliationIsAsynchronous() {
        List<String> violations = new ArrayList<>();
        for (Map.Entry<String, JsonNode> operation : operationsOfTheContract().entrySet()) {
            if (!operation.getKey().startsWith("GET ") && !operation.getValue().path("responses").has("202")) {
                violations.add(operation.getKey());
            }
        }

        assertTrue(violations.isEmpty(), "une mutation doit repondre 202 (docs 13, 04.5) : " + violations);
    }

    @Test
    void operationIdsAreUniqueAndEveryLocalReferenceResolves() {
        Set<String> ids = new TreeSet<>();
        List<String> duplicates = new ArrayList<>();
        for (Map.Entry<String, JsonNode> operation : operationsOfTheContract().entrySet()) {
            String id = operation.getValue().path("operationId").asText("");
            assertTrue(!id.isEmpty(), "operationId manquant : " + operation.getKey());
            if (!ids.add(id)) {
                duplicates.add(id);
            }
        }
        assertTrue(duplicates.isEmpty(), "operationId en double : " + duplicates);

        List<String> unresolved = new ArrayList<>();
        collectUnresolvedRefs(spec, unresolved);
        assertTrue(unresolved.isEmpty(), "references locales introuvables : " + unresolved);
    }

    private static void collectUnresolvedRefs(JsonNode node, List<String> unresolved) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && ref.asText().startsWith("#/")) {
                JsonNode target = spec;
                for (String part : ref.asText().substring(2).split("/")) {
                    target = target.path(part);
                }
                if (target.isMissingNode()) {
                    unresolved.add(ref.asText());
                }
            }
            node.fields().forEachRemaining(entry -> collectUnresolvedRefs(entry.getValue(), unresolved));
        } else if (node.isArray()) {
            node.forEach(child -> collectUnresolvedRefs(child, unresolved));
        }
    }
}
