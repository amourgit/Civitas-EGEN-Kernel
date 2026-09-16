package africa.civitas.egen.kernel.sdk.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Enveloppe generique d'un evenement publie sur le Bus d'Evenements.
 *
 * <p>Le Kernel ne connait jamais la logique de reaction a un evenement — il connait
 * seulement cette enveloppe : quoi ({@link #type()}), quand ({@link #occurredAt()}),
 * et la charge utile ({@link #payload()}) que seul le service emetteur et les
 * services souscripteurs savent interpreter. Le Kernel n'attache aucune notion de
 * perimetre applicatif ou de tenant a un evenement : cette responsabilite, si un
 * service en a besoin, appartient entierement a sa charge utile.
 *
 * @param <T> le type de la charge utile
 * @param eventId identifiant unique de cette occurrence d'evenement
 * @param type le type d'evenement
 * @param occurredAt l'horodatage auquel le fait s'est produit
 * @param payload la charge utile structuree, propre au type d'evenement
 */
public record EventEnvelope<T>(
        UUID eventId,
        EventType type,
        Instant occurredAt,
        T payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId ne peut pas etre nul.");
        Objects.requireNonNull(type, "type ne peut pas etre nul.");
        Objects.requireNonNull(occurredAt, "occurredAt ne peut pas etre nul.");
        Objects.requireNonNull(payload, "payload ne peut pas etre nul.");
    }

    /**
     * Construit une nouvelle enveloppe avec un identifiant genere et l'horodatage
     * courant — le cas d'usage normal cote emetteur.
     */
    public static <T> EventEnvelope<T> of(EventType type, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), type, Instant.now(), payload);
    }
}
