package africa.civitas.egen.adapter.consul;

import africa.civitas.egen.application.port.AdapterErrorBody;
import africa.civitas.egen.application.port.ConfigurationException;
import africa.civitas.egen.application.port.DiscoveryException;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.TargetEnvironment;
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
 * Niveau 2 (docs/architecture/17) : les messages d'erreur des deux adapters Consul (decouverte et configuration)
 * gardent le corps de la reponse, borne (constat K8, docs/architecture/24 ; docs/architecture/07, « Messages
 * d'erreur des adapters HTTP »). Faux serveur du JDK, aucun conteneur.
 */
class ConsulErrorMessageTest {

    private HttpServer server;
    private volatile int status;
    private volatile String body;
    private ConsulDiscoveryAdapter discovery;
    private ConsulConfigurationAdapter configuration;

    @BeforeEach
    void startFakeConsul() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        URI address = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        discovery = new ConsulDiscoveryAdapter(address, "tok-123");
        configuration = new ConsulConfigurationAdapter(address, "tok-123");
    }

    @AfterEach
    void stopFakeConsul() {
        server.stop(0);
    }

    @Test
    void discoveryKeepsTheRejectionBodyInTheMessage() {
        status = 403;
        body = "ACL not found";
        DiscoveryException e = assertThrows(DiscoveryException.class, () -> discovery.resolve(ServiceId.of("news")));
        assertTrue(e.getMessage().contains("HTTP 403"), e.getMessage());
        assertTrue(e.getMessage().contains("ACL not found"), e.getMessage());
    }

    @Test
    void discoveryBoundsAVeryLongRejectionBody() {
        status = 502;
        body = "<html>" + "x".repeat(20_000) + "</html>";
        DiscoveryException e = assertThrows(DiscoveryException.class, () -> discovery.resolve(ServiceId.of("news")));
        assertTrue(e.getMessage().endsWith(AdapterErrorBody.TRUNCATION_MARK), e.getMessage());
        assertTrue(e.getMessage().length() < 1_000, "longueur : " + e.getMessage().length());
    }

    @Test
    void configurationKeepsTheRejectionBodyInTheMessage() {
        status = 403;
        body = "Permission denied";
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> configuration.currentVersion(ServiceId.of("news"), TargetEnvironment.of("production")));
        assertTrue(e.getMessage().contains("HTTP 403"), e.getMessage());
        assertTrue(e.getMessage().contains("Permission denied"), e.getMessage());
    }

    @Test
    void configurationBoundsAVeryLongRejectionBody() {
        status = 502;
        body = "<html>" + "x".repeat(20_000) + "</html>";
        ConfigurationException e = assertThrows(ConfigurationException.class,
                () -> configuration.currentVersion(ServiceId.of("news"), TargetEnvironment.of("production")));
        assertTrue(e.getMessage().endsWith(AdapterErrorBody.TRUNCATION_MARK), e.getMessage());
        assertTrue(e.getMessage().length() < 1_000, "longueur : " + e.getMessage().length());
    }
}
