package africa.civitas.egen.adapter.nomad;

import africa.civitas.egen.application.port.AdapterErrorBody;
import africa.civitas.egen.application.port.DeploymentException;
import africa.civitas.egen.domain.model.ServiceId;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Niveau 2 (docs/architecture/17) : le message d'erreur de l'adapter Nomad garde le corps de la reponse, borne
 * (constat K8, docs/architecture/24 ; docs/architecture/07, « Messages d'erreur des adapters HTTP »). Faux serveur
 * du JDK, aucun conteneur.
 */
class NomadErrorMessageTest {

    private HttpServer server;
    private volatile int status;
    private volatile String body;
    private NomadDeploymentAdapter adapter;

    @BeforeEach
    void startFakeNomad() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        adapter = new NomadDeploymentAdapter(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "tok-123");
    }

    @AfterEach
    void stopFakeNomad() {
        server.stop(0);
    }

    @Test
    void theRejectionBodyIsKeptInTheMessage() {
        status = 403;
        body = "Permission denied";
        DeploymentException e = assertThrows(DeploymentException.class, () -> adapter.getStatus(ServiceId.of("news")));
        assertTrue(e.getMessage().contains("HTTP 403"), e.getMessage());
        assertTrue(e.getMessage().contains("Permission denied"), e.getMessage());
    }

    @Test
    void aVeryLongRejectionBodyIsBoundedInTheMessage() {
        status = 502;
        body = "<html>" + "x".repeat(20_000) + "</html>";
        DeploymentException e = assertThrows(DeploymentException.class, () -> adapter.getStatus(ServiceId.of("news")));
        assertTrue(e.getMessage().endsWith(AdapterErrorBody.TRUNCATION_MARK), e.getMessage());
        assertTrue(e.getMessage().length() < 1_000, "longueur : " + e.getMessage().length());
    }
}
