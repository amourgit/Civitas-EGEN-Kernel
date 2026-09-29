package africa.civitas.egen.application.observability;

import africa.civitas.egen.application.port.BindingStatus;
import africa.civitas.egen.application.port.EventHandler;
import africa.civitas.egen.application.port.MessagingPort;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.Subscription;
import africa.civitas.egen.application.port.TopicSpec;
import africa.civitas.egen.domain.event.TechnicalEvent;

/**
 * Decore un {@link MessagingPort} concret des metriques generiques
 * d'appel d'adapter — voir {@link ObservedDeploymentPort}.
 */
public final class ObservedMessagingPort implements MessagingPort {

    private final MessagingPort delegate;
    private final AdapterCallInstrumentation instrumentation;

    public ObservedMessagingPort(MessagingPort delegate, ObservabilityPort observabilityPort, String adapterName) {
        this.delegate = delegate;
        this.instrumentation = new AdapterCallInstrumentation(observabilityPort, adapterName);
    }

    @Override
    public void createTopicOrStream(TopicSpec spec) {
        instrumentation.run("messaging.createTopicOrStream", () -> delegate.createTopicOrStream(spec));
    }

    @Override
    public void publish(TechnicalEvent event, String subjectOrTopic) {
        instrumentation.run("messaging.publish", () -> delegate.publish(event, subjectOrTopic));
    }

    @Override
    public Subscription subscribe(String subjectOrTopic, String consumerGroup, EventHandler handler) {
        return instrumentation.call("messaging.subscribe",
                () -> delegate.subscribe(subjectOrTopic, consumerGroup, handler));
    }

    @Override
    public void unsubscribe(Subscription subscription) {
        instrumentation.run("messaging.unsubscribe", () -> delegate.unsubscribe(subscription));
    }

    @Override
    public BindingStatus getBindingStatus(String subjectOrTopic, String consumerGroup) {
        return instrumentation.call("messaging.getBindingStatus",
                () -> delegate.getBindingStatus(subjectOrTopic, consumerGroup));
    }
}
