package africa.civitas.egen.application.port;

/**
 * Ce qu'il faut pour creer un topic/stream de facon idempotente (voir
 * docs/architecture/07-ports-et-adapters.md, "MessagingPort.createTopicOrStream").
 */
public record TopicSpec(String name, String subjectPattern) {

    public TopicSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("TopicSpec.name ne peut pas etre vide");
        }
        if (subjectPattern == null || subjectPattern.isBlank()) {
            throw new IllegalArgumentException("TopicSpec.subjectPattern ne peut pas etre vide");
        }
    }
}
