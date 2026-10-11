package africa.civitas.egen.application.workflow;

import africa.civitas.egen.application.port.BindingStatus;
import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.EventHandler;
import africa.civitas.egen.application.port.InvocationResult;
import africa.civitas.egen.application.port.MessagingException;
import africa.civitas.egen.application.port.MessagingPort;
import africa.civitas.egen.application.port.ResolvedInstance;
import africa.civitas.egen.application.port.ResolvedInstances;
import africa.civitas.egen.application.port.ServiceInstanceRegistration;
import africa.civitas.egen.application.port.ServiceInvocationPort;
import africa.civitas.egen.application.port.Subscription;
import africa.civitas.egen.application.port.TopicSpec;
import africa.civitas.egen.domain.event.TechnicalEvent;
import africa.civitas.egen.domain.model.ServiceId;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Doubles de test du Moteur de Workflow (aucune I/O, aucun reseau). */
final class EngineFakes {

    private EngineFakes() {
    }

    record Call(String service, String method, String path, Map<String, String> payload, Duration timeout) {
        String key() {
            return method + " " + path;
        }
    }

    /** Reponses scriptees par "METHODE /chemin" ; 200 {} par defaut une fois le script epuise. */
    static final class ScriptedInvoker implements ServiceInvocationPort {
        final List<Call> calls = new ArrayList<>();
        private final Map<String, Deque<Object>> scripts = new HashMap<>();
        Consumer<Call> before = call -> { };

        void respond(String key, Object... responses) {
            scripts.computeIfAbsent(key, k -> new ArrayDeque<>()).addAll(List.of(responses));
        }

        static InvocationResult status(int code) {
            return new InvocationResult(code, Map.of());
        }

        List<String> keys() {
            return calls.stream().map(Call::key).toList();
        }

        @Override
        public InvocationResult invoke(URI endpoint, String method, Map<String, String> payload, Duration timeout) {
            Call call = new Call(endpoint.getHost() + ":" + endpoint.getPort(), method, endpoint.getPath(),
                    payload, timeout);
            calls.add(call);
            before.accept(call);
            Deque<Object> script = scripts.get(call.key());
            Object next = script == null || script.isEmpty() ? new InvocationResult(200, Map.of()) : script.poll();
            if (next instanceof RuntimeException failure) {
                throw failure;
            }
            return (InvocationResult) next;
        }
    }

    static final class FakeDiscovery implements DiscoveryPort {
        private final Set<ServiceId> unavailable = new HashSet<>();

        void makeUnavailable(ServiceId id) {
            unavailable.add(id);
        }

        @Override
        public void register(ServiceInstanceRegistration registration) {
        }

        @Override
        public void deregister(ServiceId id, String instanceId) {
        }

        @Override
        public ResolvedInstances resolve(ServiceId id) {
            if (unavailable.contains(id)) {
                return ResolvedInstances.empty();
            }
            return new ResolvedInstances(List.of(new ResolvedInstance(id.value() + "-1", id.value(), 8080)));
        }
    }

    static final class FakeMessaging implements MessagingPort {
        record Published(TechnicalEvent event, String subject) {
        }

        final List<Published> published = new ArrayList<>();
        boolean failing;

        @Override
        public void createTopicOrStream(TopicSpec spec) {
        }

        @Override
        public void publish(TechnicalEvent event, String subjectOrTopic) {
            if (failing) {
                throw new MessagingException("broker indisponible");
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

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
