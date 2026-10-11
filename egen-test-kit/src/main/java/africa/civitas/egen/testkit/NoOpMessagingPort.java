package africa.civitas.egen.testkit;

import africa.civitas.egen.application.port.BindingStatus;
import africa.civitas.egen.application.port.EventHandler;
import africa.civitas.egen.application.port.MessagingPort;
import africa.civitas.egen.application.port.Subscription;
import africa.civitas.egen.application.port.TopicSpec;
import africa.civitas.egen.domain.event.TechnicalEvent;

/** {@link MessagingPort} qui accepte et ignore toute publication — pour les tests qui n'observent pas le messaging. */
public final class NoOpMessagingPort implements MessagingPort {

    @Override
    public void createTopicOrStream(TopicSpec spec) {
    }

    @Override
    public void publish(TechnicalEvent event, String subjectOrTopic) {
    }

    @Override
    public Subscription subscribe(String subjectOrTopic, String consumerGroup, EventHandler handler) {
        throw new UnsupportedOperationException("NoOpMessagingPort ne supporte pas subscribe");
    }

    @Override
    public void unsubscribe(Subscription subscription) {
    }

    @Override
    public BindingStatus getBindingStatus(String subjectOrTopic, String consumerGroup) {
        throw new UnsupportedOperationException("NoOpMessagingPort ne supporte pas getBindingStatus");
    }
}
