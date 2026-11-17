package africa.civitas.egen.application.registry;

import africa.civitas.egen.application.port.StoredResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Niveau 1 (docs/architecture/17) : regles du stockage des reponses rejouables (docs/architecture/13). */
class InMemoryIdempotencyStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
    private static final StoredResponse FIRST =
            new StoredResponse(202, "application/json", "{\"generation\":1}", Map.of("Location", List.of("/api/v1/services/news")));

    @Test
    void anUnknownKeyIsAbsent() {
        assertTrue(new InMemoryIdempotencyStore().find("k", NOW).isEmpty());
    }

    @Test
    void aSavedResponseIsReturnedUnchangedBeforeItExpires() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        assertTrue(store.save("k", FIRST, NOW, NOW.plus(Duration.ofMinutes(10))));

        assertEquals(FIRST, store.find("k", NOW.plusSeconds(599)).orElseThrow());
    }

    @Test
    void anExpiredResponseIsInvisible() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        store.save("k", FIRST, NOW, NOW.plus(Duration.ofMinutes(10)));

        assertTrue(store.find("k", NOW.plus(Duration.ofMinutes(10))).isEmpty());
    }

    @Test
    void theFirstResponseIsKeptWhenTheSameKeyIsSavedAgain() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        store.save("k", FIRST, NOW, NOW.plusSeconds(600));

        boolean saved = store.save("k", new StoredResponse(202, null, "{\"generation\":2}", Map.of()), NOW, NOW.plusSeconds(600));

        assertFalse(saved);
        assertEquals(FIRST, store.find("k", NOW).orElseThrow());
    }

    @Test
    void nothingIsSavedOnceTheCapacityIsReached() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(2);
        assertTrue(store.save("a", FIRST, NOW, NOW.plusSeconds(600)));
        assertTrue(store.save("b", FIRST, NOW, NOW.plusSeconds(600)));

        assertFalse(store.save("c", FIRST, NOW, NOW.plusSeconds(600)));
        assertTrue(store.find("c", NOW).isEmpty());
    }

    @Test
    void expiredEntriesAreRemovedToMakeRoom() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1);
        store.save("old", FIRST, NOW, NOW.plusSeconds(10));

        assertTrue(store.save("new", FIRST, NOW.plusSeconds(11), NOW.plusSeconds(600)));
        assertEquals(1, store.size());
    }

    @Test
    void aCapacityBelowOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new InMemoryIdempotencyStore(0));
    }
}
