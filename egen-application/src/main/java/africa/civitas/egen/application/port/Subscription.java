package africa.civitas.egen.application.port;

/** Reference neutre vers une souscription active — voir {@link MessagingPort#subscribe}. */
public record Subscription(String subjectOrTopic, String consumerGroup, String nativeHandle) {

    public Subscription {
        if (subjectOrTopic == null || subjectOrTopic.isBlank()) {
            throw new IllegalArgumentException("Subscription.subjectOrTopic ne peut pas etre vide");
        }
        if (consumerGroup == null || consumerGroup.isBlank()) {
            throw new IllegalArgumentException("Subscription.consumerGroup ne peut pas etre vide");
        }
    }
}
