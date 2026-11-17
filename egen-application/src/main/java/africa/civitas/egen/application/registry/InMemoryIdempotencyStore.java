package africa.civitas.egen.application.registry;

import africa.civitas.egen.application.port.IdempotencyStorePort;
import africa.civitas.egen.application.port.StoredResponse;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stockage des reponses rejouables en memoire du processus : pour les tests et le developpement avec UN seul
 * exemplaire du Kernel. Il ne se partage pas entre exemplaires : en production on utilise l'adapter PostgreSQL.
 */
public final class InMemoryIdempotencyStore implements IdempotencyStorePort {

    /** Nombre maximal de reponses gardees par defaut (docs/architecture/13, « Idempotence »). */
    public static final int DEFAULT_MAX_ENTRIES = 10_000;

    private record Entry(StoredResponse response, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final int maxEntries;

    public InMemoryIdempotencyStore() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public InMemoryIdempotencyStore(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries doit etre superieur ou egal a 1 (recu : " + maxEntries + ")");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public Optional<StoredResponse> find(String cacheKey, Instant now) {
        Entry entry = entries.get(cacheKey);
        if (entry == null || !entry.expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    @Override
    public boolean save(String cacheKey, StoredResponse response, Instant now, Instant expiresAt) {
        entries.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
        if (entries.size() >= maxEntries) {
            return false;
        }
        return entries.putIfAbsent(cacheKey, new Entry(response, expiresAt)) == null;
    }

    /** Nombre d'entrees gardees (expirees non retirees comprises) : pour les tests. */
    public int size() {
        return entries.size();
    }
}
