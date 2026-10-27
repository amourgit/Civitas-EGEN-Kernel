package africa.civitas.egen.testkit;

import africa.civitas.egen.application.port.BindingStatus;
import africa.civitas.egen.application.port.EventHandler;
import africa.civitas.egen.application.port.MessagingException;
import africa.civitas.egen.application.port.MessagingPort;
import africa.civitas.egen.application.port.Subscription;
import africa.civitas.egen.application.port.TopicSpec;
import africa.civitas.egen.domain.event.TechnicalEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@link MessagingPort} qui enregistre chaque publication (evenement + sujet) — pour les tests qui
 * verifient CE QUI est relaye au broker. {@link #failOnPublish} simule un broker injoignable.
 */
public class RecordingMessagingPort implements MessagingPort {

    public record Published(TechnicalEvent event, String subject) { }

    private final List<Published> published = new CopyOnWriteArrayList<>();
    private volatile boolean failOnPublish;

    public List<Published> published() {
        return List.copyOf(published);
    }

    public void clear() {
        published.clear();
    }

    public void failOnPublish(boolean failing) {
        this.failOnPublish = failing;
    }

    @Override
    public void createTopicOrStream(TopicSpec spec) {
    }

    @Override
    public void publish(TechnicalEvent event, String subjectOrTopic) {
        if (failOnPublish) {
            throw new MessagingException("broker injoignable");
        }
        published.add(new Published(event, subjectOrTopic));
    }

    @Override
    public Subscription subscribe(String subjectOrTopic, String consumerGroup, EventHandler handler) {
        throw new UnsupportedOperationException("RecordingMessagingPort ne supporte pas subscribe");
    }

    @Override
    public void unsubscribe(Subscription subscription) {
    }

    @Override
    public BindingStatus getBindingStatus(String subjectOrTopic, String consumerGroup) {
        throw new UnsupportedOperationException("RecordingMessagingPort ne supporte pas getBindingStatus");
    }
}
