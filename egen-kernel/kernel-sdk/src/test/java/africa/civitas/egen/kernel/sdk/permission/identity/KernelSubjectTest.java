package africa.civitas.egen.kernel.sdk.permission.identity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelSubjectTest {

    @Test
    void bootstrapAlwaysCarriesTheReservedId() {
        KernelSubject sujet = KernelSubject.bootstrap();

        assertEquals(KernelSubject.BOOTSTRAP_ID, sujet.id());
        assertTrue(sujet.bootstrap());
    }

    @Test
    void bootstrapIsCanonicalAcrossCalls() {
        assertEquals(KernelSubject.bootstrap(), KernelSubject.bootstrap());
    }

    @Test
    void deBuildsANonBootstrapSubject() {
        UUID id = UUID.randomUUID();
        KernelSubject sujet = KernelSubject.de(id);

        assertEquals(id, sujet.id());
        assertFalse(sujet.bootstrap());
    }

    @Test
    void nouveauGeneratesADistinctNonBootstrapSubjectEachTime() {
        KernelSubject premier = KernelSubject.nouveau();
        KernelSubject second = KernelSubject.nouveau();

        assertFalse(premier.bootstrap());
        assertNotEquals(premier.id(), second.id());
    }

    @Test
    void rejectsANullId() {
        assertThrows(IllegalArgumentException.class, () -> new KernelSubject(null, false));
    }

    @Test
    void rejectsABootstrapSubjectWithAnArbitraryId() {
        assertThrows(IllegalArgumentException.class,
                () -> new KernelSubject(UUID.randomUUID(), true));
    }

    @Test
    void rejectsTheReservedIdOnANonBootstrapSubject() {
        assertThrows(IllegalArgumentException.class,
                () -> new KernelSubject(KernelSubject.BOOTSTRAP_ID, false));
    }
}
