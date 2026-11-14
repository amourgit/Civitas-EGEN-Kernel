package africa.civitas.egen.api.rest;

import africa.civitas.egen.application.port.DiscoveryException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Niveau 1 (docs/architecture/17) : quand la decouverte (Consul) echoue, {@code GET /api/v1/discover/{nom}} repond
 * {@code 503} et non un {@code 500} generique (ecart E1, docs/architecture/13).
 */
class DiscoverResourceUnavailableTest {

    @Test
    void whenTheDiscoveryFailsTheAnswerIs503() {
        DiscoverResource resource = new DiscoverResource(id -> {
            throw new DiscoveryException("Consul a rejete resolve(news) : HTTP 500 — boom");
        });
        assertThrows(ServiceUnavailableException.class, () -> resource.discover("news"));
    }

    @Test
    void aMalformedIdIsStillA400BeforeAnyDiscoveryCall() {
        DiscoverResource resource = new DiscoverResource(id -> {
            throw new AssertionError("la decouverte ne doit pas etre appelee");
        });
        assertThrows(BadRequestException.class, () -> resource.discover("Pas Un Id"));
    }
}
