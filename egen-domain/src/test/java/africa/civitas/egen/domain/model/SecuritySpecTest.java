package africa.civitas.egen.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Niveau 1 (docs/architecture/17) : regles de deployment.security (docs/architecture/06, constat K9). */
class SecuritySpecTest {

    @Test
    void aUserIdOfZeroIsRefusedBecauseItIsRoot() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new SecuritySpec(null, 0, null, null));
        assertTrue(error.getMessage().contains("deployment.security.runAsUser"), error.getMessage());
        assertTrue(error.getMessage().contains("root"), error.getMessage());
    }

    @Test
    void aNegativeUserIdIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new SecuritySpec(null, -5, null, null));
    }

    @Test
    void theSmallestAndTheLargestUserIdsAreAccepted() {
        assertEquals(Integer.valueOf(1), new SecuritySpec(null, 1, null, null).runAsUser());
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), new SecuritySpec(null, Integer.MAX_VALUE, null, null).runAsUser());
    }

    @Test
    void anAbsentFieldStaysAbsentAndAFalseFieldStaysFalse() {
        SecuritySpec spec = new SecuritySpec(false, null, null, null);
        assertEquals(Boolean.FALSE, spec.runAsNonRoot());
        assertNull(spec.readOnlyRootFilesystem());
        assertFalse(spec.isEmpty());
    }

    @Test
    void aBlockWithNoFieldIsEmpty() {
        assertTrue(new SecuritySpec(null, null, null, null).isEmpty());
    }

    @Test
    void anEmptyBlockIsTheSameAsNoBlockInADeploymentSpec() {
        DeploymentSpec none = new DeploymentSpec("kubernetes", "img:1", "100m", "64Mi", new ReplicaRange(1, 2));
        DeploymentSpec empty = new DeploymentSpec("kubernetes", "img:1", "100m", "64Mi", new ReplicaRange(1, 2),
                new SecuritySpec(null, null, null, null));
        assertNull(empty.security());
        assertEquals(none, empty);
    }

    @Test
    void aNonEmptyBlockIsKept() {
        SecuritySpec security = new SecuritySpec(true, 10001, true, true);
        DeploymentSpec spec = new DeploymentSpec("kubernetes", "img:1", "100m", "64Mi", new ReplicaRange(1, 2),
                security);
        assertEquals(security, spec.security());
    }
}
