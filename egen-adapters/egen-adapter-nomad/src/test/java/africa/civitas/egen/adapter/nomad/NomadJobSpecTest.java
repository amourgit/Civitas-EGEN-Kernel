package africa.civitas.egen.adapter.nomad;

import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.ServiceId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le job REELLEMENT soumis a Nomad (docs/architecture/07, Deployment Port ; 20, etape 4). Capture le corps de
 * {@code PUT /v1/jobs} : c'est lui, et non une methode privee, que Nomad execute.
 */
class NomadJobSpecTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer nomad;
    private final List<String> submitted = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void startNomad() throws IOException {
        nomad = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        nomad.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (exchange.getRequestMethod().equals("PUT") && exchange.getRequestURI().getPath().equals("/v1/jobs")) {
                submitted.add(body);
            }
            byte[] response = "{\"EvalID\":\"eval-1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        nomad.start();
    }

    @AfterEach
    void stopNomad() {
        nomad.stop(0);
    }

    private JsonNode submitJob() throws IOException {
        NomadDeploymentAdapter adapter = new NomadDeploymentAdapter(
                URI.create("http://127.0.0.1:" + nomad.getAddress().getPort()), null);
        adapter.create(ServiceId.of("news-service"),
                new DeploymentSpec("nomad", "registry.civitas.africa/news-service:2.4.0", "500m", "512Mi", new ReplicaRange(2, 6)));
        assertEquals(1, submitted.size(), "un seul job doit etre soumis");
        return JSON.readTree(submitted.get(0)).path("Job");
    }

    @Test
    void theDockerTaskPublishesTheDynamicPortItsApplicationListensOn() throws IOException {
        JsonNode config = submitJob().path("TaskGroups").get(0).path("Tasks").get(0).path("Config");

        assertEquals("registry.civitas.africa/news-service:2.4.0", config.path("image").asText());
        assertEquals(List.of("http"), List.of(config.path("ports").get(0).asText()),
                "sans « ports » le port dynamique n'est publie nulle part : la sonde Consul echoue en connection refused");
        assertEquals(1, config.path("ports").size());
    }

    @Test
    void thePublishedLabelIsTheOneOfTheGroupDynamicPort() throws IOException {
        JsonNode group = submitJob().path("TaskGroups").get(0);

        String declared = group.path("Networks").get(0).path("DynamicPorts").get(0).path("Label").asText();
        String published = group.path("Tasks").get(0).path("Config").path("ports").get(0).asText();
        assertEquals(declared, published, "« ports » doit referencer l'etiquette du port dynamique du groupe");
    }

    @Test
    void theJobCarriesTheDeclaredReplicasAndResources() throws IOException {
        JsonNode job = submitJob();
        JsonNode group = job.path("TaskGroups").get(0);

        assertEquals("news-service", job.path("ID").asText());
        assertEquals("service", job.path("Type").asText());
        assertEquals(2, group.path("Count").asInt(), "le nombre d'instances initial est replicas.min");
        assertEquals("docker", group.path("Tasks").get(0).path("Driver").asText());
        assertTrue(group.path("Tasks").get(0).path("Resources").path("MemoryMB").asInt() > 0);
        assertFalse(group.path("Tasks").get(0).path("Config").path("image").asText().isBlank());
    }
}
