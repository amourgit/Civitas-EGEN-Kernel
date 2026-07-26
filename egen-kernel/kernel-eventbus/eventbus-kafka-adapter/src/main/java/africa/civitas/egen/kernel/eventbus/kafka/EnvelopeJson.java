package africa.civitas.egen.kernel.eventbus.kafka;

import java.time.Instant;
import java.util.UUID;

/**
 * Miroir non generique de {@code EventEnvelope} (kernel-sdk), destine uniquement a
 * la (de)serialisation JSON via Jackson : un record generique ({@code T payload})
 * introduit une ambiguite de type a la deserialisation que Jackson ne resout qu'avec
 * une information de type explicite, plus fragile a maintenir correctement qu'une
 * classe intermediaire simple. {@code payload} traverse Kafka comme une structure
 * JSON generique (typiquement une {@code Map<String,Object>} une fois deserialisee)
 * — voir le javadoc de {@link KafkaEventBusAdapter} pour la consequence exacte cote
 * souscripteur.
 *
 * <p>Champs publics mutables a dessein : uniquement (de)serialisee par Jackson,
 * jamais manipulee ni exposee ailleurs dans ce module.
 */
public class EnvelopeJson {

    public UUID eventId;
    public String type;
    public UUID contexteId;
    public Instant occurredAt;
    public Object payload;

    /** Constructeur sans argument requis par Jackson. */
    public EnvelopeJson() {
    }

    public EnvelopeJson(UUID eventId, String type, UUID contexteId, Instant occurredAt, Object payload) {
        this.eventId = eventId;
        this.type = type;
        this.contexteId = contexteId;
        this.occurredAt = occurredAt;
        this.payload = payload;
    }
}
