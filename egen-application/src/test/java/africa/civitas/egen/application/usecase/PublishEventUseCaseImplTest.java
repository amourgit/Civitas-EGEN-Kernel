package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.BindingStatus;
import africa.civitas.egen.application.port.EventHandler;
import africa.civitas.egen.application.port.MessagingException;
import africa.civitas.egen.application.port.MessagingPort;
import africa.civitas.egen.application.port.Subscription;
import africa.civitas.egen.application.port.TopicSpec;
import africa.civitas.egen.application.registry.InMemoryRegistryStore;
import africa.civitas.egen.domain.event.TechnicalEvent;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.EventSpec;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.LifecyclePolicy;
import africa.civitas.egen.domain.model.OwnerTeam;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import africa.civitas.egen.domain.model.TargetEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Niveau 2 (docs/architecture/17) : « un appelant ne publie que ce qu'un manifeste annonce » (docs/12, 13). */
class PublishEventUseCaseImplTest {

    private static final String TYPE = "africa.civitas.news.article.created.v1";

    private InMemoryRegistryStore registry;
    private RecordingMessaging messaging;
    private PublishEventUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistryStore();
        messaging = new RecordingMessaging();
        useCase = new PublishEventUseCaseImpl(registry, messaging);
    }

    private void declare(String name, OwnerTeam team, EventSpec events) {
        ServiceManifest manifest = new ServiceManifest(ServiceId.of(name), ServiceVersion.parse("1.0.0"),
                new ServiceRuntime(RuntimeType.CONTAINER, "registry/" + name + ":1.0.0", "java"),
                new DeploymentSpec("nomad", "registry/" + name + ":1.0.0", "500m", "512Mi", new ReplicaRange(1, 2)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                new LifecyclePolicy(Duration.ofMillis(50)), List.of(), team, null, events, null, null);
        registry.save(new DesiredState(manifest, 0L, TargetEnvironment.of("test")));
    }

    private static EventSpec publishing(String type) {
        return new EventSpec(List.of(new EventSpec.Published(type, null)), List.of());
    }

    private static PublishEventCommand command(String type) {
        return new PublishEventCommand(type, "/news-service", null, null, "{\"articleId\":\"42\"}");
    }

    @Test
    void relaysAnAnnouncedTypeToItsSubjectWithoutTouchingData() {
        declare("news-service", OwnerTeam.unassigned(), publishing(TYPE));

        TechnicalEvent event = useCase.publish(command(TYPE), team -> true);

        assertEquals(1, messaging.published.size());
        Published sent = messaging.published.get(0);
        assertEquals(TYPE, sent.subject);
        assertEquals(TYPE, sent.event.type());
        assertEquals("{\"articleId\":\"42\"}", sent.event.data());
        assertEquals("/news-service", sent.event.source());
        assertEquals(event.id(), sent.event.id());
    }

    @Test
    void generatesACorrelationIdWhenAbsentAndKeepsTheOneGiven() {
        declare("news-service", OwnerTeam.unassigned(), publishing(TYPE));

        TechnicalEvent generated = useCase.publish(command(TYPE), team -> true);
        TechnicalEvent given = useCase.publish(
                new PublishEventCommand(TYPE, "/s", "corr-4471", "corr-4470", "{}"), team -> true);

        assertNotNull(generated.correlationId());
        assertNull(generated.causationId());
        assertEquals("corr-4471", given.correlationId());
        assertEquals("corr-4470", given.causationId());
    }

    @Test
    void refusesATypeThatNoDeclaredServiceAnnounces() {
        declare("news-service", OwnerTeam.unassigned(), publishing(TYPE));

        assertThrows(EventPublicationNotAllowedException.class,
                () -> useCase.publish(command("africa.civitas.other.thing.v1"), team -> true));
        assertTrue(messaging.published.isEmpty());
    }

    @Test
    void aConsumedTypeIsNotAPublishedType() {
        declare("notif-service", OwnerTeam.unassigned(),
                new EventSpec(List.of(), List.of(new EventSpec.Consumed(TYPE, "g", null))));

        assertThrows(EventPublicationNotAllowedException.class, () -> useCase.publish(command(TYPE), team -> true));
        assertTrue(messaging.published.isEmpty());
    }

    @Test
    void refusesACallerAuthorisedOnNoPublisher() {
        declare("news-service", new OwnerTeam("content-platform"), publishing(TYPE));

        assertThrows(EventPublicationNotAllowedException.class,
                () -> useCase.publish(command(TYPE), team -> "billing".equals(team.value())));
        assertTrue(messaging.published.isEmpty());
    }

    @Test
    void acceptsACallerAuthorisedOnOneOfSeveralPublishers() {
        declare("news-service", new OwnerTeam("content-platform"), publishing(TYPE));
        declare("blog-service", new OwnerTeam("blog"), publishing(TYPE));

        useCase.publish(command(TYPE), team -> "blog".equals(team.value()));

        assertEquals(1, messaging.published.size());
    }

    @Test
    void aBrokerFailurePropagatesAndNothingIsRecorded() {
        declare("news-service", OwnerTeam.unassigned(), publishing(TYPE));
        messaging.failing = true;

        assertThrows(MessagingException.class, () -> useCase.publish(command(TYPE), team -> true));
        assertTrue(messaging.published.isEmpty());
    }

    @Test
    void aCommandWithoutTypeOrSourceOrDataIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new PublishEventCommand(" ", "/s", null, null, "{}"));
        assertThrows(IllegalArgumentException.class, () -> new PublishEventCommand(TYPE, "", null, null, "{}"));
        assertThrows(NullPointerException.class, () -> new PublishEventCommand(TYPE, "/s", null, null, null));
    }

    private record Published(TechnicalEvent event, String subject) { }

    /** Double local : egen-test-kit depend de egen-application, il ne peut donc pas etre utilise ici. */
    private static final class RecordingMessaging implements MessagingPort {
        final List<Published> published = new ArrayList<>();
        boolean failing;

        @Override
        public void createTopicOrStream(TopicSpec spec) {
        }

        @Override
        public void publish(TechnicalEvent event, String subjectOrTopic) {
            if (failing) {
                throw new MessagingException("broker injoignable");
            }
            published.add(new Published(event, subjectOrTopic));
        }

        @Override
        public Subscription subscribe(String subjectOrTopic, String consumerGroup, EventHandler handler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unsubscribe(Subscription subscription) {
        }

        @Override
        public BindingStatus getBindingStatus(String subjectOrTopic, String consumerGroup) {
            throw new UnsupportedOperationException();
        }
    }
}
