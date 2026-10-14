package africa.civitas.egen.bootstrap.scenario;

import africa.civitas.egen.adapter.httpinvocation.HttpInvocationAdapter;
import africa.civitas.egen.adapter.postgresregistry.PostgresWorkflowStoreAdapter;
import africa.civitas.egen.api.dto.WorkflowDefinitionDto;
import africa.civitas.egen.api.mapper.WorkflowMapper;
import africa.civitas.egen.application.port.BindingStatus;
import africa.civitas.egen.application.port.DiscoveryPort;
import africa.civitas.egen.application.port.EventHandler;
import africa.civitas.egen.application.port.InvocationResult;
import africa.civitas.egen.application.port.MessagingPort;
import africa.civitas.egen.application.port.ResolvedInstance;
import africa.civitas.egen.application.port.ResolvedInstances;
import africa.civitas.egen.application.port.ServiceInstanceRegistration;
import africa.civitas.egen.application.port.ServiceInvocationPort;
import africa.civitas.egen.application.port.Subscription;
import africa.civitas.egen.application.port.TopicSpec;
import africa.civitas.egen.application.resilience.ResiliencyPolicyProvider;
import africa.civitas.egen.application.workflow.Sleeper;
import africa.civitas.egen.application.workflow.WorkflowEngine;
import africa.civitas.egen.application.workflow.WorkflowEngineSettings;
import africa.civitas.egen.domain.event.TechnicalEvent;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.StepId;
import africa.civitas.egen.domain.workflow.StepResult;
import africa.civitas.egen.domain.workflow.StepStatus;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowExecutionStatus;
import africa.civitas.egen.domain.workflow.WorkflowInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario d'acceptation du Moteur de Workflow — docs/architecture/20, partie 2
 * (etapes 11, 12 et 13). Vrai {@link WorkflowEngine}, vrai adapter HTTP, vrai
 * PostgreSQL (Testcontainers) et de vrais serveurs HTTP de fixtures ; le YAML
 * du doc 11 est parse TEL QUEL. Ne sont PAS reels ici : Nomad, Consul (la
 * decouverte est en memoire) et NATS (le messaging est un enregistreur) —
 * leur rejeu complet est le test de niveau 5 de l'environnement complet
 * (docs/architecture/17).
 *
 * <p>Chaque test declare son propre workflow (nom unique) : le conteneur est
 * partage par toute la classe.</p>
 */
@Testcontainers
class PublishArticleWorkflowScenarioIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Le workflow de reference, recopie du doc 11 (docs/architecture/11-moteur-de-workflow.md). */
    private static final String DOC_11_YAML = """
            apiVersion: egen.civitas.africa/v1
            kind: WorkflowDefinition
            metadata:
              name: publish-article-workflow
            spec:
              steps:
                - id: validate-content
                  service: news-service
                  operation: POST /internal/validate
                  timeout: 10s
                  retry: { maxAttempts: 3, backoff: exponential }
                - id: reserve-notification-slot
                  service: notification-service
                  operation: POST /internal/reserve-slot
                  dependsOn: [validate-content]
                  compensation:
                    operation: POST /internal/release-slot
                - id: publish
                  service: news-service
                  operation: POST /internal/publish
                  dependsOn: [reserve-notification-slot]
                  compensation:
                    operation: POST /internal/unpublish
                - id: notify-subscribers
                  service: notification-service
                  operation: EVENT africa.civitas.news.article.published.v1   # etape asynchrone
                  dependsOn: [publish]
              compensationStrategy: backward
              executionPolicy:
                globalTimeout: 5m
                manualApprovalSteps: []
            """;

    /** Panne simulee du Kernel : une Error n'est attrapee par aucune boucle du moteur (comme un kill -9). */
    private static final class SimulatedCrash extends Error {
        private static final long serialVersionUID = 1L;

        SimulatedCrash() {
            super("arret brutal simule du Kernel");
        }
    }

    /** Service de fixture : repond 200 par defaut, statuts scriptes par "METHODE /chemin", appels journalises dans l'ordre. */
    private static final class FixtureService {
        final HttpServer server;
        private final Map<String, Deque<Integer>> scripts = new HashMap<>();

        FixtureService(String name, List<String> sharedLog) throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                String key = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
                exchange.getRequestBody().readAllBytes();
                sharedLog.add(name + " " + key);
                int status;
                synchronized (scripts) {
                    Deque<Integer> script = scripts.get(key);
                    status = script == null || script.isEmpty() ? 200 : script.poll();
                }
                byte[] body = "{\"ok\":\"true\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
        }

        FixtureService respond(String key, int... statuses) {
            synchronized (scripts) {
                Deque<Integer> script = scripts.computeIfAbsent(key, k -> new ArrayDeque<>());
                for (int s : statuses) {
                    script.add(s);
                }
            }
            return this;
        }

        int port() {
            return server.getAddress().getPort();
        }
    }

    private static final class FixtureDiscovery implements DiscoveryPort {
        private final Map<String, Integer> ports = new HashMap<>();

        void expose(String service, int port) {
            ports.put(service, port);
        }

        @Override
        public void register(ServiceInstanceRegistration registration) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deregister(ServiceId id, String instanceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResolvedInstances resolve(ServiceId id) {
            Integer port = ports.get(id.value());
            return port == null ? ResolvedInstances.empty()
                    : new ResolvedInstances(List.of(new ResolvedInstance(id.value() + "-1", "127.0.0.1", port)));
        }
    }

    private static final class RecordingMessaging implements MessagingPort {
        final List<TechnicalEvent> published = Collections.synchronizedList(new ArrayList<>());
        final List<String> subjects = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void createTopicOrStream(TopicSpec spec) {
        }

        @Override
        public void publish(TechnicalEvent event, String subjectOrTopic) {
            published.add(event);
            subjects.add(subjectOrTopic);
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

    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    private FixtureService news;
    private FixtureService notification;
    private FixtureDiscovery discovery;
    private RecordingMessaging messaging;

    @BeforeEach
    void startFixtures() throws IOException {
        news = new FixtureService("news-service", calls);
        notification = new FixtureService("notification-service", calls);
        discovery = new FixtureDiscovery();
        discovery.expose("news-service", news.port());
        discovery.expose("notification-service", notification.port());
        messaging = new RecordingMessaging();
    }

    @AfterEach
    void stopFixtures() {
        news.server.stop(0);
        notification.server.stop(0);
    }

    private static DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    /** Un Kernel : son propre store (donc sa propre connexion) sur la MEME base — "redemarrer" = en creer un autre. */
    private WorkflowEngine kernel(ServiceInvocationPort invocation) {
        return new WorkflowEngine(new PostgresWorkflowStoreAdapter(dataSource()), discovery, invocation, messaging,
                Clock.systemUTC(), noWait(), Runnable::run, WorkflowEngineSettings.defaults(),
                ResiliencyPolicyProvider.defaultsOnly());
    }

    /** Les attentes entre tentatives n'apportent rien au scenario : on ne dort pas. */
    private static Sleeper noWait() {
        return duration -> { };
    }

    private static WorkflowDefinition publishArticleWorkflow(String uniqueName) throws IOException {
        ObjectMapper yaml = new YAMLMapper();
        WorkflowDefinitionDto dto = yaml.readValue(
                DOC_11_YAML.replace("name: publish-article-workflow", "name: " + uniqueName),
                WorkflowDefinitionDto.class);
        return WorkflowMapper.toDomain(dto);
    }

    private static String uniqueName() {
        return "publish-article-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static WorkflowExecution reload(ExecutionId id) {
        // Un store tout neuf : ce qui est lu vient de PostgreSQL, jamais d'un objet reste en memoire.
        return new PostgresWorkflowStoreAdapter(dataSource()).findById(id).orElseThrow();
    }

    private static List<String> statuses(WorkflowExecution execution, String stepId) {
        return execution.stepResults().stream().filter(r -> r.stepId().equals(StepId.of(stepId)))
                .map(r -> r.status().name() + "#" + r.attempt()).toList();
    }

    // ------------------------------------------------------------------ etape 11

    @Test
    void nominalRunCompletesInDependencyOrderAndPublishesTheCorrelatedEvent() throws Exception {
        ExecutionId id = kernel(new HttpInvocationAdapter())
                .start(publishArticleWorkflow(uniqueName()), new WorkflowInput(Map.of("articleId", "42")));

        WorkflowExecution execution = reload(id);
        assertEquals(WorkflowExecutionStatus.COMPLETED, execution.status());
        assertEquals(List.of("news-service POST /internal/validate",
                        "notification-service POST /internal/reserve-slot",
                        "news-service POST /internal/publish"), calls);
        assertEquals(List.of("validate-content", "reserve-notification-slot", "publish", "notify-subscribers"),
                execution.succeededStepsInExecutionOrder().stream().map(StepId::value).toList());
        assertEquals(Map.of("articleId", "42"), execution.input());

        assertEquals(1, messaging.published.size());
        assertEquals("africa.civitas.news.article.published.v1", messaging.subjects.get(0));
        assertEquals(id.value(), messaging.published.get(0).correlationId());
        assertEquals("{\"articleId\":\"42\"}", messaging.published.get(0).data());
    }

    // ------------------------------------------------------------------ etape 12

    @Test
    void aFailingIntermediateStepCompensatesTheSucceededOnesInReverseOrder() throws Exception {
        news.respond("POST /internal/publish", 500);

        ExecutionId id = kernel(new HttpInvocationAdapter())
                .start(publishArticleWorkflow(uniqueName()), new WorkflowInput(Map.of("articleId", "42")));

        WorkflowExecution execution = reload(id);
        assertEquals(WorkflowExecutionStatus.COMPENSATED, execution.status());
        assertEquals(List.of("news-service POST /internal/validate",
                        "notification-service POST /internal/reserve-slot",
                        "news-service POST /internal/publish",
                        "notification-service POST /internal/release-slot"), calls,
                "release-slot compense reserve-slot ; publish a echoue : jamais unpublish ; validate n'a pas de compensation");
        assertEquals(List.of("SUCCEEDED#1"), statuses(execution, "validate-content"));
        assertEquals(List.of("SUCCEEDED#1", "COMPENSATED#2"), statuses(execution, "reserve-notification-slot"));
        assertEquals(List.of("FAILED#1"), statuses(execution, "publish"));
        assertTrue(execution.latestResultFor(StepId.of("publish")).orElseThrow().errorMessage().contains("500"));
        assertEquals(List.of(), statuses(execution, "notify-subscribers"));
        assertTrue(messaging.published.isEmpty(), "aucun evenement ne doit partir apres une compensation");
    }

    @Test
    void aRetryableFailureIsRetriedWithTheDeclaredPolicyBeforeSucceeding() throws Exception {
        news.respond("POST /internal/validate", 503, 503); // retry: maxAttempts 3 dans le workflow

        ExecutionId id = kernel(new HttpInvocationAdapter())
                .start(publishArticleWorkflow(uniqueName()), new WorkflowInput(Map.of("articleId", "42")));

        WorkflowExecution execution = reload(id);
        assertEquals(WorkflowExecutionStatus.COMPLETED, execution.status());
        assertEquals(List.of("FAILED#1", "FAILED#2", "SUCCEEDED#3"), statuses(execution, "validate-content"),
                "un fait final par tentative, jamais ecrase (cle unique execution/etape/tentative)");
    }

    // ------------------------------------------------------------------ etape 13

    @Test
    void aKernelCrashMidRunIsResumedByTheNextKernelWithoutReplayingDoneSteps() throws Exception {
        HttpInvocationAdapter real = new HttpInvocationAdapter();
        AtomicInteger invocations = new AtomicInteger();
        ServiceInvocationPort crashesOnTheSecondCall = (URI endpoint, String method, Map<String, String> payload,
                                                        Duration timeout) -> {
            if (invocations.incrementAndGet() == 2) {
                throw new SimulatedCrash();
            }
            return real.invoke(endpoint, method, payload, timeout);
        };
        WorkflowDefinition definition = publishArticleWorkflow(uniqueName());

        // Kernel n°1 : valide l'article, puis "meurt" avant d'appeler reserve-slot.
        assertThrows(SimulatedCrash.class, () -> kernel(crashesOnTheSecondCall)
                .start(definition, new WorkflowInput(Map.of("articleId", "42"))));
        ExecutionId id = new PostgresWorkflowStoreAdapter(dataSource()).findRunningExecutions().stream()
                .filter(candidate -> reload(candidate).workflowId().equals(definition.id()))
                .findFirst().orElseThrow();
        WorkflowExecution interrupted = reload(id);
        assertEquals(WorkflowExecutionStatus.RUNNING, interrupted.status());
        assertEquals(List.of("validate-content"),
                interrupted.succeededStepsInExecutionOrder().stream().map(StepId::value).toList());
        assertEquals(Map.of("articleId", "42"), interrupted.input(), "l'entree est persistee avec l'execution");
        assertEquals(List.of("news-service POST /internal/validate"), calls);

        // Kernel n°2 : redemarrage -> reprise.
        assertTrue(kernel(real).recoverInterruptedExecutions() >= 1);

        WorkflowExecution resumed = reload(id);
        assertEquals(WorkflowExecutionStatus.COMPLETED, resumed.status());
        assertEquals(List.of("news-service POST /internal/validate",
                        "notification-service POST /internal/reserve-slot",
                        "news-service POST /internal/publish"), calls, "validate n'est pas rejouee");
        assertEquals(1, messaging.published.size());
        assertEquals("{\"articleId\":\"42\"}", messaging.published.get(0).data(),
                "les etapes reprises utilisent l'entree d'origine");
    }
}
