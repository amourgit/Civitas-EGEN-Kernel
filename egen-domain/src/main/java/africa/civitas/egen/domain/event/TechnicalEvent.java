package africa.civitas.egen.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Enveloppe CloudEvents interne (voir
 * docs/architecture/07-ports-et-adapters.md, "L'enveloppe d'evenement :
 * adopter CloudEvents"). {@code data} est un payload OPAQUE pour EGEN —
 * jamais deserialise ni interprete par le Kernel, uniquement transporte.
 */
public record TechnicalEvent(String id, String source, String type, Instant time,
                              String correlationId, String causationId, String data) {

    private static final String SPEC_VERSION = "1.0";

    public TechnicalEvent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("TechnicalEvent.id ne peut pas etre vide");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("TechnicalEvent.source ne peut pas etre vide");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("TechnicalEvent.type ne peut pas etre vide");
        }
        if (time == null) {
            throw new IllegalArgumentException("TechnicalEvent.time est obligatoire");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("TechnicalEvent.correlationId ne peut pas etre vide");
        }
        if (data == null) {
            throw new IllegalArgumentException("TechnicalEvent.data ne peut pas etre nul");
        }
    }

    public String specVersion() {
        return SPEC_VERSION;
    }

    /**
     * Cree un nouvel evenement racine (pas de causationId — voir
     * {@link #causedBy}) avec un id genere et l'horodatage courant.
     */
    public static TechnicalEvent create(String source, String type, String correlationId, String data) {
        return new TechnicalEvent(UUID.randomUUID().toString(), source, type, Instant.now(),
                correlationId, null, data);
    }

    /** Cree un evenement qui porte la meme correlation et se declare cause par {@code causeEventId}. */
    public TechnicalEvent causedBy(String causeEventId, String newSource, String newType, String newData) {
        return new TechnicalEvent(UUID.randomUUID().toString(), newSource, newType, Instant.now(),
                this.correlationId, causeEventId, newData);
    }
}
