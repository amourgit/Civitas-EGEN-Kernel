package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.nats.NatsMessagingAdapter;
import africa.civitas.egen.application.port.MessagingPort;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Seul point du Kernel qui sait que le MessagingPort est, en V1, implemente
 * par NATS JetStream (voir docs/architecture/07-ports-et-adapters.md,
 * "Kafka vs NATS JetStream" — NATS retenu par defaut, egen-adapter-kafka
 * rejoindra cette methode en V2 comme option, jamais par anticipation).
 * Remplacer ou ajouter un second Messaging Adapter se fait en changeant
 * uniquement cette methode (garde-fou n6,
 * docs/architecture/02-principes-fondamentaux.md).
 */
@ApplicationScoped
public class MessagingAdapterBeans {

    private NatsMessagingAdapter adapter;

    @Produces
    @ApplicationScoped
    public MessagingPort messagingPort(
            @ConfigProperty(name = "egen.nats.url", defaultValue = "nats://localhost:4222")
            String natsUrl) {
        if (adapter == null) {
            adapter = new NatsMessagingAdapter(natsUrl);
        }
        return adapter;
    }

    void onStop(@Observes ShutdownEvent event) throws InterruptedException {
        if (adapter != null) {
            adapter.close();
        }
    }
}
