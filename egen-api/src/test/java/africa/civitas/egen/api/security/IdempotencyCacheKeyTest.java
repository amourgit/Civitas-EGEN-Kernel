package africa.civitas.egen.api.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Niveau 1 (docs/architecture/17) : la clé d'idempotence est propre à l'appelant, sans conteneur. */
class IdempotencyCacheKeyTest {

    @Test
    void sameCallerSameRequestSameKey() {
        assertEquals(IdempotencyCacheKey.of("alice", "POST", "api/v1/services", "k-1"),
                IdempotencyCacheKey.of("alice", "POST", "api/v1/services", "k-1"));
    }

    @Test
    void twoDifferentCallersNeverShareAnEntryEvenWithTheSameHeaderValue() {
        assertNotEquals(IdempotencyCacheKey.of("alice", "POST", "api/v1/services", "k-1"),
                IdempotencyCacheKey.of("bob", "POST", "api/v1/services", "k-1"));
    }

    @Test
    void methodPathAndHeaderValueAreEachPartOfTheKey() {
        String base = IdempotencyCacheKey.of("alice", "POST", "api/v1/services", "k-1");
        assertNotEquals(base, IdempotencyCacheKey.of("alice", "PUT", "api/v1/services", "k-1"));
        assertNotEquals(base, IdempotencyCacheKey.of("alice", "POST", "api/v1/workflows", "k-1"));
        assertNotEquals(base, IdempotencyCacheKey.of("alice", "POST", "api/v1/services", "k-2"));
    }

    @Test
    void componentBoundariesCannotBeForgedBySeparatorCharacters() {
        // Un appelant qui choisit « bob|POST » comme valeur ne peut pas se faire passer pour bob.
        assertNotEquals(IdempotencyCacheKey.of("alice", "POST", "p", "x"),
                IdempotencyCacheKey.of("alice|POST", "p", "x", ""));
        assertNotEquals(IdempotencyCacheKey.of("a", "b", "c", "d"), IdempotencyCacheKey.of("a", "b", "cd", ""));
        assertNotEquals(IdempotencyCacheKey.of("ab", "c", "d", "e"), IdempotencyCacheKey.of("a", "bc", "d", "e"));
        assertNotEquals(IdempotencyCacheKey.of("a", "p", "q;1:r", "s"), IdempotencyCacheKey.of("a", "p", "q", "1:r;s"));
    }

    @Test
    void aMissingCallerIsOneNamedAnonymousNotAWildcardSharedWithRealCallers() {
        assertEquals(IdempotencyCacheKey.of(null, "POST", "p", "k"), IdempotencyCacheKey.of(" ", "POST", "p", "k"));
        assertNotEquals(IdempotencyCacheKey.of(null, "POST", "p", "k"), IdempotencyCacheKey.of("alice", "POST", "p", "k"));
        assertNotEquals(IdempotencyCacheKey.of("alice", "POST", "p", "k"),
                IdempotencyCacheKey.of(IdempotencyCacheKey.ANONYMOUS.substring(1), "POST", "p", "k"));
    }
}
