package africa.civitas.egen.kernel.policy.api.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValeurEffectiveTest {

    @Test
    void withoutDerogationIdIndicatesTheValueComesFromThePolitiqueItself() {
        ValeurEffective v = new ValeurEffective("12 caracteres", null);

        assertFalse(v.resulteDuneDerogation());
        assertTrue(v.derogationAppliquee().isEmpty());
    }

    @Test
    void withADerogationIdIndicatesItWasApplied() {
        UUID derogationId = UUID.randomUUID();
        ValeurEffective v = new ValeurEffective("8 caracteres", derogationId);

        assertTrue(v.resulteDuneDerogation());
        assertTrue(v.derogationAppliquee().isPresent());
    }

    @Test
    void rejectsABlankValeur() {
        assertThrows(IllegalArgumentException.class, () -> new ValeurEffective(" ", null));
    }
}
