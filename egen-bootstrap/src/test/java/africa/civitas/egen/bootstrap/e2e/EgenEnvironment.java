package africa.civitas.egen.bootstrap.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.Capability;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Environnement ephemere COMPLET du test de niveau 5 (docs/architecture/17, « Niveau 5 » ; 20 — Scenario de
 * bout en bout) : PostgreSQL, NATS (JetStream), Consul, Vault et un Nomad qui execute reellement des
 * allocations. Seul le Kernel n'est pas conteneurise : c'est lui, le systeme sous test, demarre dans la JVM
 * du test par Quarkus et configure pour parler a ces conteneurs.
 *
 * <p><b>Pourquoi Nomad est « privilegie » et en reseau hote.</b> L'adapter Nomad genere un job de driver
 * {@code docker} avec un port dynamique que l'application lit dans {@code NOMAD_PORT_http}. Pour qu'un service
 * converge reellement jusqu'a {@code RUNNING}, Nomad doit donc etre un CLIENT avec acces au demon Docker (socket
 * monte ; les conteneurs de service sont des freres, pas des enfants) :</p>
 * <ul>
 *   <li>{@code /tmp} est monte a l'identique : le demon Docker resout les repertoires d'allocation de Nomad
 *       sur l'HOTE, ils doivent y exister au meme chemin ;</li>
 *   <li>reseau hote : les taches sont dans le reseau de l'hote, Nomad annonce donc une adresse (celle de l'hote)
 *       a laquelle le Kernel — dans la JVM du test — et Consul peuvent reellement joindre le service.</li>
 * </ul>
 *
 * <p>L'IT de niveau 3 de l'adapter Nomad s'arrete volontairement avant l'etat {@code running} (Nomad serveur
 * seul) et renvoie la convergence reelle a ce test.</p>
 */
public class EgenEnvironment implements QuarkusTestResourceLifecycleManager {

    public static final String VAULT_TOKEN = "egen-e2e-root-token";
    public static final String FIXTURE_IMAGE = "egen-e2e-fixture:e2e";
    public static final String NOMAD_URL = "http://localhost:4646";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> nats;
    private static GenericContainer<?> consul;
    private static GenericContainer<?> vault;
    private static GenericContainer<?> nomad;

    public static String consulUrl() {
        return "http://localhost:" + consul.getMappedPort(8500);
    }

    public static String natsMonitoringUrl() {
        return "http://localhost:" + nats.getMappedPort(8222);
    }

    public static String vaultUrl() {
        return "http://localhost:" + vault.getMappedPort(8200);
    }

    @Override
    public Map<String, String> start() {
        // Le tag n'est pas « latest » : le driver docker de Nomad tente de re-telecharger une image « latest ».
        new ImageFromDockerfile(FIXTURE_IMAGE, false)
                .withFileFromClasspath("server.py", "e2e/fixture/server.py")
                .withDockerfileFromBuilder(builder -> builder
                        .from("python:3.12-alpine")
                        .copy("server.py", "/server.py")
                        .cmd("python", "-u", "/server.py")
                        .build())
                .get();

        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("egen_registry").withUsername("egen").withPassword("egen");
        nats = new GenericContainer<>("nats:2.10-alpine")
                .withCommand("-js", "-m", "8222")
                .withExposedPorts(4222, 8222)
                .waitingFor(Wait.forHttp("/healthz").forPort(8222).withStartupTimeout(Duration.ofSeconds(60)));
        consul = new GenericContainer<>("hashicorp/consul:1.19")
                .withCommand("agent", "-dev", "-client=0.0.0.0")
                .withExposedPorts(8500)
                .waitingFor(Wait.forHttp("/v1/status/leader").forStatusCode(200)
                        .forResponsePredicate(body -> body.length() > 4)
                        .withStartupTimeout(Duration.ofSeconds(90)));
        vault = new GenericContainer<>("hashicorp/vault:1.13")
                .withEnv("VAULT_DEV_ROOT_TOKEN_ID", VAULT_TOKEN)
                .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withCapAdd(Capability.IPC_LOCK))
                .withExposedPorts(8200)
                .waitingFor(Wait.forHttp("/v1/sys/health").forPort(8200).forStatusCode(200)
                        .withStartupTimeout(Duration.ofSeconds(90)));
        nomad = new GenericContainer<>("hashicorp/nomad:1.8")
                .withCommand("agent", "-dev", "-bind=0.0.0.0", "-log-level=INFO")
                .withPrivilegedMode(true)
                .withNetworkMode("host")
                .withFileSystemBind("/var/run/docker.sock", "/var/run/docker.sock")
                .withFileSystemBind("/tmp", "/tmp");

        // En parallele : les images a telecharger sont l'essentiel du temps de demarrage.
        java.util.List.of(postgres, nats, consul, vault, nomad).parallelStream().forEach(GenericContainer::start);
        awaitNomadDockerDriver();

        return Map.of(
                "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
                "quarkus.datasource.username", postgres.getUsername(),
                "quarkus.datasource.password", postgres.getPassword(),
                "egen.nomad.address", NOMAD_URL,
                "egen.consul.address", consulUrl(),
                "egen.nats.url", "nats://localhost:" + nats.getMappedPort(4222),
                "egen.vault.address", vaultUrl(),
                "egen.vault.token", VAULT_TOKEN);
    }

    @Override
    public void stop() {
        for (GenericContainer<?> container : new GenericContainer<?>[] {nomad, vault, consul, nats, postgres}) {
            if (container != null) {
                try {
                    container.stop();
                } catch (RuntimeException ignored) {
                    // Nettoyage au mieux : ne masque jamais l'echec du test.
                }
            }
        }
    }

    // ------------------------------------------------------------------ attente de Nomad

    /** Nomad est pret quand son noeud est « ready » ET que le driver docker y est detecte et sain. */
    private static void awaitNomadDockerDriver() {
        long deadline = System.nanoTime() + Duration.ofSeconds(150).toNanos();
        String last = "aucune reponse";
        while (System.nanoTime() < deadline) {
            try {
                JsonNode nodes = getJson(NOMAD_URL + "/v1/nodes");
                if (nodes.isArray() && !nodes.isEmpty() && "ready".equals(nodes.get(0).path("Status").asText())) {
                    JsonNode node = getJson(NOMAD_URL + "/v1/node/" + nodes.get(0).path("ID").asText());
                    JsonNode docker = node.path("Drivers").path("docker");
                    if (docker.path("Detected").asBoolean() && docker.path("Healthy").asBoolean()) {
                        return;
                    }
                    last = "driver docker : " + docker;
                } else {
                    last = "noeuds : " + nodes;
                }
            } catch (Exception e) {
                last = e.toString();
            }
            sleep(1000);
        }
        throw new IllegalStateException("Nomad n'est pas pret (client + driver docker) apres 150 s — dernier etat : "
                + last + "\n--- journal de Nomad ---\n" + tail(nomad.getLogs(), 4000));
    }

    // ------------------------------------------------------------------ utilitaires partages avec le test

    public static JsonNode getJson(String url) throws IOException, InterruptedException {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET()
                .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " pour " + url);
        }
        return JSON.readTree(response.body());
    }

    public static int statusOf(String method, String url, String body, String... headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** Ce que le test montre quand une attente expire : l'etat de Nomad et la fin de son journal. */
    public static String diagnostics() {
        StringBuilder out = new StringBuilder("\n--- Nomad : jobs ---\n");
        try {
            out.append(getJson(NOMAD_URL + "/v1/jobs").toPrettyString());
            out.append("\n--- Nomad : allocations ---\n");
            for (JsonNode alloc : getJson(NOMAD_URL + "/v1/allocations")) {
                out.append(alloc.path("JobID").asText()).append(" ")
                        .append(alloc.path("ID").asText(), 0, 8).append(" client=")
                        .append(alloc.path("ClientStatus").asText()).append(" ")
                        .append(alloc.path("TaskStates").toString()).append('\n');
            }
        } catch (Exception e) {
            out.append("(Nomad injoignable : ").append(e).append(")\n");
        }
        out.append("--- journal de Nomad (fin) ---\n").append(tail(nomad.getLogs(), 6000));
        return out.toString();
    }

    private static String tail(String text, int max) {
        return text.length() <= max ? text : text.substring(text.length() - max);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
