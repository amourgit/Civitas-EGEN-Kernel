package africa.civitas.egen.application.port;

import africa.civitas.egen.domain.event.TechnicalEvent;

/**
 * Port secondaire — ce que le domaine exige de tout moteur de messaging
 * (NATS JetStream en V1, voir docs/architecture/07-ports-et-adapters.md,
 * "Messaging Port"). {@code createTopicOrStream} est idempotent (no-op si
 * deja existant, garde-fou n7,
 * docs/architecture/02-principes-fondamentaux.md).
 */
public interface MessagingPort {

    void createTopicOrStream(TopicSpec spec);

    void publish(TechnicalEvent event, String subjectOrTopic);

    Subscription subscribe(String subjectOrTopic, String consumerGroup, EventHandler handler);

    void unsubscribe(Subscription subscription);

    BindingStatus getBindingStatus(String subjectOrTopic, String consumerGroup);
}
