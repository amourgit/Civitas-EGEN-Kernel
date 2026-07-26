package africa.civitas.egen.kernel.eventbus.api;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AbonnementTest {

    @Test
    void rejectsANullId() {
        assertThrows(NullPointerException.class, () -> new Abonnement(null, "academie", "description"));
    }

    @Test
    void rejectsABlankModuleId() {
        assertThrows(IllegalArgumentException.class, () -> new Abonnement(UUID.randomUUID(), " ", "description"));
    }

    @Test
    void rejectsABlankDescription() {
        assertThrows(IllegalArgumentException.class, () -> new Abonnement(UUID.randomUUID(), "academie", " "));
    }
}
