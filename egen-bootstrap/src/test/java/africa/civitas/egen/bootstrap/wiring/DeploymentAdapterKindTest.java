package africa.civitas.egen.bootstrap.wiring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Niveau 1 (docs/architecture/17) : la regle de selection de egen.deployment.adapter, sans conteneur. */
class DeploymentAdapterKindTest {

    @Test
    void nomadIsTheDefault() {
        assertEquals(DeploymentAdapterKind.NOMAD, DeploymentAdapterKind.parse(DeploymentAdapterKind.DEFAULT));
    }

    @Test
    void bothKnownAdaptersAreRecognisedIgnoringCaseAndSpaces() {
        assertEquals(DeploymentAdapterKind.NOMAD, DeploymentAdapterKind.parse("nomad"));
        assertEquals(DeploymentAdapterKind.KUBERNETES, DeploymentAdapterKind.parse("kubernetes"));
        assertEquals(DeploymentAdapterKind.KUBERNETES, DeploymentAdapterKind.parse("  Kubernetes "));
    }

    @Test
    void anUnknownValueFailsAndListsTheAllowedOnes() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> DeploymentAdapterKind.parse("docker"));
        assertTrue(e.getMessage().contains("docker"), e.getMessage());
        assertTrue(e.getMessage().contains("nomad") && e.getMessage().contains("kubernetes"), e.getMessage());
    }

    @Test
    void blankOrNullIsRejectedNotSilentlyDefaulted() {
        assertThrows(IllegalStateException.class, () -> DeploymentAdapterKind.parse(""));
        assertThrows(IllegalStateException.class, () -> DeploymentAdapterKind.parse(null));
    }

    @Test
    void propertyValuesMatchTheNamesUsedByTheMetrics() {
        assertEquals("nomad", DeploymentAdapterKind.NOMAD.propertyValue());
        assertEquals("kubernetes", DeploymentAdapterKind.KUBERNETES.propertyValue());
    }
}
