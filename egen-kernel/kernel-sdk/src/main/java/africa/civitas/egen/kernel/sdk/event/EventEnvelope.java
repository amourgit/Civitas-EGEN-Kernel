package africa.civitas.egen.kernel.sdk.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Enveloppe generique d'un evenement publie sur le Bus d'Evenements.
 *
 * <p>Le Kernel ne connait jamais la logique de reaction a un evenement — il connait
 * seulement cette enveloppe : qui (quel {@code contexteId}), quoi ({@link #type()}),
 * quand ({@link #occurredAt()}), et la charge utile ({@link #payload()}) que seul le
 * module emetteur et les modules souscripteurs savent interpreter.
 *
 * <p>Chaque evenement porte obligatoirement un {@code contexteId} : conformement au
 * principe du Contexte unifie, aucun fait metier n'est publie hors d'un Contexte
 * (Organisation ou Cellule) precis.
 *
 * @param <T> le type de la charge utile
 * @param eventId identifiant unique de cette occurrence d'evenement
 * @param type le type d'evenement
 * @param contexteId le Contexte (Organisation ou Cellule) porteur du fait
 * @param occurredAt l'horodatage auquel le fait s'est produit
 * @param payload la charge utile structurea, propre au type d'evenement
 */
public record EventEnvelope<T>(
        UUID eventId,
        EventType type,
        UUID contexteId,
        Instant occurredAt,
        T payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId ne peut pas etre nul.");
        Objects.requireNonNull(type, "type ne peut pas etre nul.");
        Objects.requireNonNull(contexteId, "contexteId ne peut pas etre nul : "
                + "tout evenement doit etre rattache a un Contexte.");
        Objects.requireNonNull(occurredAt, "occurredAt ne peut pas etre nul.");
        Objects.requireNonNull(payload, "payload ne peut pas etre nul.");
    }

    /**
     * Construit une nouvelle enveloppe avec un identifiant genere et l'horodatage
     * courant — le cas d'usage normal cote emetteur.
     */
    public static <T> EventEnvelope<T> of(EventType type, UUID contexteId, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), type, contexteId, Instant.now(), payload);
    }
}
