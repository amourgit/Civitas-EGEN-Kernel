package africa.civitas.egen.kernel.sdk.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventEnvelopeTest {

    private static final EventType SAMPLE_TYPE = new EventType("identite.personne.creee");

    private record PersonnePayload(String nom) {
    }

    @Test
    void factoryGeneratesAUniqueIdAndACurrentTimestamp() {
        UUID contexteId = UUID.randomUUID();
        Instant before = Instant.now();

        EventEnvelope<PersonnePayload> envelope =
                EventEnvelope.of(SAMPLE_TYPE, contexteId, new PersonnePayload("Samuel"));

        Instant after = Instant.now();

        assertNotNull(envelope.eventId());
        assertEquals(SAMPLE_TYPE, envelope.type());
        assertEquals(contexteId, envelope.contexteId());
        assertEquals("Samuel", envelope.payload().nom());
        assertFalse(envelope.occurredAt().isBefore(before));
        assertFalse(envelope.occurredAt().isAfter(after));
    }

    @Test
    void twoEnvelopesBuiltFromTheSameCallHaveDifferentIdentifiers() {
        UUID contexteId = UUID.randomUUID();

        EventEnvelope<PersonnePayload> first =
                EventEnvelope.of(SAMPLE_TYPE, contexteId, new PersonnePayload("Samuel"));
        EventEnvelope<PersonnePayload> second =
                EventEnvelope.of(SAMPLE_TYPE, contexteId, new PersonnePayload("Samuel"));

        assertTrue(!first.eventId().equals(second.eventId()),
                "Deux occurrences distinctes du meme fait doivent porter des identifiants distincts.");
    }

    @Test
    void rejectsAMissingContexte() {
        assertThrows(NullPointerException.class,
                () -> new EventEnvelope<>(UUID.randomUUID(), SAMPLE_TYPE, null, Instant.now(),
                        new PersonnePayload("Samuel")));
    }

    @Test
    void rejectsAMissingType() {
        assertThrows(NullPointerException.class,
                () -> new EventEnvelope<>(UUID.randomUUID(), null, UUID.randomUUID(), Instant.now(),
                        new PersonnePayload("Samuel")));
    }

    @Test
    void rejectsAMissingPayload() {
        assertThrows(NullPointerException.class,
                () -> new EventEnvelope<PersonnePayload>(UUID.randomUUID(), SAMPLE_TYPE,
                        UUID.randomUUID(), Instant.now(), null));
    }
}
