package africa.civitas.egen.kernel.bootstrap.config;

import africa.civitas.egen.kernel.eventbus.api.EventBus;
import africa.civitas.egen.kernel.eventbus.api.EventHandler;
import africa.civitas.egen.kernel.sdk.event.EventEnvelope;
import africa.civitas.egen.kernel.sdk.event.EventType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifie que {@link KernelBootConfig#eventBus()} produit reellement un bean CDI
 * {@link EventBus} injectable et fonctionnel dans l'application assemblee.
 *
 * <p>Jusqu'a cette livraison, {@code EventBus} etait construit et teste isolement
 * (voir {@code InMemoryEventBusTest} dans eventbus-api) mais aucun {@code @Produces}
 * ne l'exposait dans kernel-bootstrap : aucune classe du reacteur ne pouvait
 * l'injecter ni l'utiliser. Ce test ne duplique pas la couverture fonctionnelle
 * deja assuree par {@code InMemoryEventBusTest} (souscription par prefixe,
 * isolation des gestionnaires en echec, etc.) — il verifie seulement que le
 * cablage CDI, lui, existe desormais reellement.
 */
@QuarkusTest
class KernelBootConfigEventBusTest {

    private static final EventType TYPE_DE_TEST = new EventType("kernel-bootstrap.test.evenement");

    @Inject
    EventBus eventBus;

    @Test
    void eventBusIsARealInjectableCdiBean() {
        assertNotNull(eventBus);
    }

    @Test
    void aHandlerSubscribedThroughTheInjectedBusReceivesAPublishedEvent() {
        List<EventEnvelope<String>> recus = new ArrayList<>();
        eventBus.souscrire("kernel-bootstrap-test", TYPE_DE_TEST, (EventHandler<String>) recus::add);

        EventEnvelope<String> evenement = EventEnvelope.of(TYPE_DE_TEST, "charge-utile-de-test");
        try {
            eventBus.publier(evenement);

            assertEquals(1, recus.size());
            assertEquals(evenement, recus.get(0));
        } finally {
            eventBus.desabonnerToutPour("kernel-bootstrap-test");
        }
    }
}
