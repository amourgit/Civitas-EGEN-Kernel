package africa.civitas.egen.modules.business.organization.api.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FermetureTransitiveCelluleTest {

    @Test
    void selfReferenceAtDepthZeroIsValid() {
        UUID id = UUID.randomUUID();
        assertDoesNotThrow(() -> new FermetureTransitiveCellule(id, id, 0));
    }

    @Test
    void distinctCellulesAtDepthOneIsValid() {
        assertDoesNotThrow(() -> new FermetureTransitiveCellule(UUID.randomUUID(), UUID.randomUUID(), 1));
    }

    @Test
    void rejectsDepthZeroWithDistinctCellules() {
        assertThrows(IllegalArgumentException.class,
                () -> new FermetureTransitiveCellule(UUID.randomUUID(), UUID.randomUUID(), 0));
    }

    @Test
    void rejectsSelfReferenceAtNonZeroDepth() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new FermetureTransitiveCellule(id, id, 2));
    }

    @Test
    void rejectsNegativeDepth() {
        assertThrows(IllegalArgumentException.class,
                () -> new FermetureTransitiveCellule(UUID.randomUUID(), UUID.randomUUID(), -1));
    }
}
