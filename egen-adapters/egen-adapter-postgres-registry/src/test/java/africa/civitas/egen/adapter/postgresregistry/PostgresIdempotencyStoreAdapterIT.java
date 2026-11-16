package africa.civitas.egen.adapter.postgresregistry;

import africa.civitas.egen.application.port.StoredResponse;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de niveau 3 (docs/architecture/17) : un vrai PostgreSQL via Testcontainers. Le test central de l'etape P0-3
 * est {@link #aResponseSavedByOneInstanceIsReplayedByAnotherInstanceSharingTheDatabase} : deux adapters distincts,
 * comme deux exemplaires du Kernel, sur la meme base.
 */
@Testcontainers
class PostgresIdempotencyStoreAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    private static DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    /** Chaque methode utilise sa propre cle : le conteneur est partage entre les methodes. */
    private static String uniqueKey() {
        return "caller;POST;/api/v1/services;" + UUID.randomUUID();
    }

    private static StoredResponse aResponse() {
        return new StoredResponse(202, "application/json", "{\"serviceId\":\"news\",\"generation\":1}",
                Map.of("Location", List.of("/api/v1/services/news"), "X-Multi", List.of("a", "b")));
    }

    @Test
    void aResponseSavedByOneInstanceIsReplayedByAnotherInstanceSharingTheDatabase() {
        PostgresIdempotencyStoreAdapter instanceA = new PostgresIdempotencyStoreAdapter(dataSource());
        PostgresIdempotencyStoreAdapter instanceB = new PostgresIdempotencyStoreAdapter(dataSource());
        String key = uniqueKey();

        assertTrue(instanceA.save(key, aResponse(), NOW, NOW.plusSeconds(600)));

        assertEquals(aResponse(), instanceB.find(key, NOW.plusSeconds(1)).orElseThrow());
    }

    @Test
    void anUnknownKeyIsAbsent() {
        assertTrue(new PostgresIdempotencyStoreAdapter(dataSource()).find(uniqueKey(), NOW).isEmpty());
    }

    @Test
    void aResponseWithoutBodyOrMediaTypeSurvivesTheRoundTrip() {
        PostgresIdempotencyStoreAdapter store = new PostgresIdempotencyStoreAdapter(dataSource());
        String key = uniqueKey();
        StoredResponse empty = new StoredResponse(204, null, null, Map.of());

        store.save(key, empty, NOW, NOW.plusSeconds(600));

        assertEquals(empty, store.find(key, NOW).orElseThrow());
    }

    @Test
    void anExpiredResponseIsInvisible() {
        PostgresIdempotencyStoreAdapter store = new PostgresIdempotencyStoreAdapter(dataSource());
        String key = uniqueKey();
        store.save(key, aResponse(), NOW, NOW.plusSeconds(600));

        assertTrue(store.find(key, NOW.plusSeconds(600)).isEmpty());
    }

    @Test
    void theFirstResponseIsKeptWhenTheSameKeyIsSavedAgain() {
        PostgresIdempotencyStoreAdapter store = new PostgresIdempotencyStoreAdapter(dataSource());
        String key = uniqueKey();
        store.save(key, aResponse(), NOW, NOW.plusSeconds(600));

        boolean saved = store.save(key, new StoredResponse(202, null, "{\"generation\":2}", Map.of()), NOW,
                NOW.plusSeconds(600));

        assertFalse(saved);
        assertEquals(aResponse(), store.find(key, NOW).orElseThrow());
    }

    @Test
    void aVeryLongKeyIsAccepted() {
        PostgresIdempotencyStoreAdapter store = new PostgresIdempotencyStoreAdapter(dataSource());
        String key = "x".repeat(20_000) + UUID.randomUUID();

        assertTrue(store.save(key, aResponse(), NOW, NOW.plusSeconds(600)));
        assertTrue(store.find(key, NOW).isPresent());
    }

    @Test
    void nothingIsSavedOnceTheCapacityIsReached() {
        // Capacite 1 sur une table deja peuplee par les autres tests : la capacite est atteinte des le depart.
        PostgresIdempotencyStoreAdapter tiny = new PostgresIdempotencyStoreAdapter(dataSource(), 1);
        tiny.save(uniqueKey(), aResponse(), NOW, NOW.plusSeconds(600));
        String refused = uniqueKey();

        assertFalse(tiny.save(refused, aResponse(), NOW, NOW.plusSeconds(600)));
        assertTrue(tiny.find(refused, NOW).isEmpty());
    }
}
