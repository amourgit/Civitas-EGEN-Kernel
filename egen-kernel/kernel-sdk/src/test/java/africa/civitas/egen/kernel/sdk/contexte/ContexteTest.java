package africa.civitas.egen.kernel.sdk.contexte;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ContexteTest {

    /**
     * Implementation minimale utilisee uniquement pour verifier que le contrat
     * Contexte est utilisable tel quel par un consommateur qui n'a jamais vu la
     * moindre implementation Niveau 2 — exactement la situation d'un module metier
     * externe, quel que soit le domaine qu'il modelise.
     */
    private record TestContexte(UUID id) implements Contexte {
    }

    @Test
    void exposesItsOwnIdentifier() {
        UUID id = UUID.randomUUID();
        Contexte contexte = new TestContexte(id);

        assertEquals(id, contexte.id());
    }

    @Test
    void twoDistinctContextesAreDistinguishableByIdentifier() {
        Contexte premier = new TestContexte(UUID.randomUUID());
        Contexte second = new TestContexte(UUID.randomUUID());

        assertNotEquals(premier.id(), second.id());
    }
}
