package africa.civitas.egen.adapter.nats;

import africa.civitas.egen.application.port.BindingStatus;
import africa.civitas.egen.application.port.Subscription;
import africa.civitas.egen.application.port.TopicSpec;
import africa.civitas.egen.domain.event.TechnicalEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test de niveau 3 (voir docs/architecture/17-strategie-de-tests.md) : demarre
 * un vrai NATS JetStream via Testcontainers et verifie le mapping de
 * {@link NatsMessagingAdapter} contre l'API reelle — un fixture publie un
 * evenement CloudEvents, un autre le consomme via MessagingPort, exactement
 * le scenario attendu en Phase 3 (voir
 * docs/architecture/19-feuille-de-route.md).
 */
@Testcontainers
class NatsMessagingAdapterIT {

    @Container
    static final GenericContainer<?> NATS = new GenericContainer<>("nats:2.10-alpine")
            .withExposedPorts(4222)
            .withCommand("-js")
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))
            .withStartupTimeout(Duration.ofSeconds(60));

    private NatsMessagingAdapter adapter;

    @BeforeEach
    void setUp() {
        String url = "nats://" + NATS.getHost() + ":" + NATS.getMappedPort(4222);
        adapter = new NatsMessagingAdapter(url);
    }

    @AfterEach
    void tearDown() throws Exception {
        adapter.close();
    }

    @Test
    void oneFixturePublishesACloudEventTheOtherConsumesThroughMessagingPort() throws InterruptedException {
        String subject = "news.article.created";
        adapter.createTopicOrStream(new TopicSpec("NEWS", "news.article.>"));

        BlockingQueue<TechnicalEvent> received = new ArrayBlockingQueue<>(1);
        Subscription subscription = adapter.subscribe(subject, "notification-service-consumers",
                received::add);

        TechnicalEvent published = TechnicalEvent.create("/news-service",
                "africa.civitas.news.article.created.v1", "corr-1", "{\"articleId\":\"art-123\"}");
        adapter.publish(published, subject);

        TechnicalEvent consumed = received.poll(10, TimeUnit.SECONDS);
        assertNotNull(consumed, "l'evenement publie doit etre recu par le consumer NATS reel");
        assertEquals(published.id(), consumed.id());
        assertEquals(published.type(), consumed.type());
        assertEquals(published.correlationId(), consumed.correlationId());

        adapter.unsubscribe(subscription);
    }

    @Test
    void createTopicOrStreamIsIdempotent() {
        TopicSpec spec = new TopicSpec("IDEMPOTENCE_TEST", "idempotence.test.>");
        adapter.createTopicOrStream(spec);
        adapter.createTopicOrStream(spec); // deuxieme appel : ne doit pas lever d'exception
    }

    @Test
    void bindingStatusForAnUnknownSubjectReturnsEmpty() {
        BindingStatus status = adapter.getBindingStatus("never.declared.subject", "some-group");
        assertEquals(0, status.pendingMessages());
    }
}
