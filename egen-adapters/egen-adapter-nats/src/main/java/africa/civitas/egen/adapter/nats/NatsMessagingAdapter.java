package africa.civitas.egen.adapter.nats;

import africa.civitas.egen.application.port.BindingStatus;
import africa.civitas.egen.application.port.EventHandler;
import africa.civitas.egen.application.port.MessagingException;
import africa.civitas.egen.application.port.MessagingPort;
import africa.civitas.egen.application.port.Subscription;
import africa.civitas.egen.application.port.TopicSpec;
import africa.civitas.egen.domain.event.TechnicalEvent;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.PublishAck;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation du {@link MessagingPort} contre NATS JetStream reel — voir
 * docs/architecture/07-ports-et-adapters.md, "Mapping vers l'API — NATS
 * JetStream" pour le mapping complet. Seul point du Kernel a parler au
 * client NATS.
 *
 * <p>L'enveloppe {@link TechnicalEvent} (CloudEvents) est serialisee en JSON
 * minimal directement ici — {@code data} reste une chaine opaque, jamais
 * interpretee (voir docs/architecture/07-ports-et-adapters.md,
 * "L'enveloppe d'evenement").</p>
 */
public final class NatsMessagingAdapter implements MessagingPort, AutoCloseable {

    private final Connection connection;
    private final JetStream jetStream;
    private final JetStreamManagement jetStreamManagement;
    // Association subject -> nom de stream, alimentee par createTopicOrStream :
    // JetStream n'expose pas de lookup "stream par subject" simple et stable
    // cote client — le Kernel est seul emetteur de cette association, il la
    // garde donc lui-meme plutot que de la re-derivee a chaque appel.
    private final Map<String, String> streamNameBySubject = new ConcurrentHashMap<>();
    private final Map<String, JetStreamSubscription> activeSubscriptions = new ConcurrentHashMap<>();

    /**
     * Se connecte lui-meme a NATS a partir de l'URL fournie — le client NATS
     * (io.nats.client.*) ne fuit jamais hors de ce module (garde-fou n2,
     * docs/architecture/02-principes-fondamentaux.md) : egen-bootstrap ne
     * cable qu'une chaine de connexion, jamais un objet {@link Connection}.
     */
    public NatsMessagingAdapter(String natsUrl) {
        try {
            this.connection = Nats.connect(natsUrl);
            this.jetStream = connection.jetStream();
            this.jetStreamManagement = connection.jetStreamManagement();
        } catch (IOException e) {
            throw new MessagingException("Connexion NATS impossible (" + natsUrl + ") : "
                    + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingException("Connexion NATS interrompue (" + natsUrl + ")", e);
        }
    }

    @Override
    public void close() throws InterruptedException {
        connection.close();
    }

    @Override
    public void createTopicOrStream(TopicSpec spec) {
        streamNameBySubject.put(spec.subjectPattern(), spec.name());
        try {
            try {
                jetStreamManagement.getStreamInfo(spec.name());
                return; // deja existant — idempotent (garde-fou n7)
            } catch (JetStreamApiException notFound) {
                // 404 attendu si le stream n'existe pas encore : on le cree ci-dessous.
            }
            StreamConfiguration configuration = StreamConfiguration.builder()
                    .name(spec.name())
                    .subjects(spec.subjectPattern())
                    .storageType(StorageType.File)
                    .build();
            jetStreamManagement.addStream(configuration);
        } catch (IOException | JetStreamApiException e) {
            throw new MessagingException("Creation du stream \"" + spec.name() + "\" impossible : "
                    + e.getMessage(), e);
        }
    }

    @Override
    public void publish(TechnicalEvent event, String subjectOrTopic) {
        try {
            // Publication synchrone, avec accuse de reception : garantit la
            // persistance cote NATS avant de considerer l'evenement "publie"
            // (voir docs/architecture/07-ports-et-adapters.md, mapping NATS).
            PublishAck ack = jetStream.publish(subjectOrTopic, toCloudEventsJson(event)
                    .getBytes(StandardCharsets.UTF_8));
            if (ack.hasError()) {
                throw new MessagingException("NATS a rejete la publication sur \"" + subjectOrTopic
                        + "\" : " + ack.getError());
            }
        } catch (IOException | JetStreamApiException e) {
            throw new MessagingException("Publication impossible sur \"" + subjectOrTopic + "\" : "
                    + e.getMessage(), e);
        }
    }

    @Override
    public Subscription subscribe(String subjectOrTopic, String consumerGroup, EventHandler handler) {
        try {
            Dispatcher dispatcher = connection.createDispatcher();
            PushSubscribeOptions options = PushSubscribeOptions.builder()
                    .durable(consumerGroup)
                    .build();
            JetStreamSubscription nativeSubscription = jetStream.subscribe(subjectOrTopic, dispatcher,
                    message -> handleMessage(message, handler), false, options);

            String handle = UUID.randomUUID().toString();
            activeSubscriptions.put(handle, nativeSubscription);
            return new Subscription(subjectOrTopic, consumerGroup, handle);
        } catch (IOException | JetStreamApiException e) {
            throw new MessagingException("Souscription impossible sur \"" + subjectOrTopic
                    + "\" (groupe \"" + consumerGroup + "\") : " + e.getMessage(), e);
        }
    }

    private void handleMessage(Message message, EventHandler handler) {
        handler.onEvent(fromCloudEventsJson(new String(message.getData(), StandardCharsets.UTF_8)));
        message.ack();
    }

    @Override
    public void unsubscribe(Subscription subscription) {
        JetStreamSubscription nativeSubscription = activeSubscriptions.remove(subscription.nativeHandle());
        if (nativeSubscription != null && nativeSubscription.isActive()) {
            nativeSubscription.unsubscribe();
        }
    }

    @Override
    public BindingStatus getBindingStatus(String subjectOrTopic, String consumerGroup) {
        String streamName = streamNameBySubject.get(subjectOrTopic);
        if (streamName == null) {
            return BindingStatus.empty();
        }
        try {
            ConsumerInfo info = jetStreamManagement.getConsumerInfo(streamName, consumerGroup);
            return new BindingStatus(info.getNumPending(), info.getNumAckPending(),
                    info.getRedelivered());
        } catch (IOException | JetStreamApiException e) {
            throw new MessagingException("Lecture du statut de souscription impossible pour \""
                    + consumerGroup + "\" sur \"" + subjectOrTopic + "\" : " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Enveloppe CloudEvents — serialisation JSON explicite via l'API arbre
    // de Jackson (voir NomadDeploymentAdapter/ConsulDiscoveryAdapter pour
    // le meme choix de controle total sur le mapping). "data" reste opaque
    // pour EGEN : incorpore comme sous-arbre JSON brut, jamais interprete
    // (voir docs/architecture/07-ports-et-adapters.md, "L'enveloppe
    // d'evenement").
    // ------------------------------------------------------------------

    private final ObjectMapper mapper = new ObjectMapper();

    private String toCloudEventsJson(TechnicalEvent event) {
        ObjectNode node = mapper.createObjectNode();
        node.put("specversion", event.specVersion());
        node.put("id", event.id());
        node.put("source", event.source());
        node.put("type", event.type());
        node.put("time", event.time().toString());
        node.put("datacontenttype", "application/json");
        node.put("egen_correlation_id", event.correlationId());
        if (event.causationId() != null) {
            node.put("egen_causation_id", event.causationId());
        }
        try {
            node.set("data", mapper.readTree(event.data()));
        } catch (IOException e) {
            throw new MessagingException("TechnicalEvent.data n'est pas un JSON valide : "
                    + e.getMessage(), e);
        }
        try {
            return mapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new MessagingException("Serialisation CloudEvents impossible : " + e.getMessage(), e);
        }
    }

    private TechnicalEvent fromCloudEventsJson(String json) {
        JsonNode node;
        try {
            node = mapper.readTree(json);
        } catch (IOException e) {
            throw new MessagingException("Message NATS illisible : " + e.getMessage(), e);
        }
        JsonNode causation = node.get("egen_causation_id");
        return new TechnicalEvent(
                node.path("id").asText(),
                node.path("source").asText(),
                node.path("type").asText(),
                Instant.parse(node.path("time").asText()),
                node.path("egen_correlation_id").asText(),
                causation == null ? null : causation.asText(),
                node.path("data").toString());
    }
}
