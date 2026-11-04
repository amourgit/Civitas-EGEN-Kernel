package africa.civitas.egen.bootstrap.wiring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Niveau 1 (docs/architecture/17) : la lecture de egen.deployment.adapters, sans conteneur. */
class DeploymentAdapterKindTest {

    @Test
    void nomadAloneIsTheDefault() {
        assertEquals(List.of(DeploymentAdapterKind.NOMAD),
                List.copyOf(DeploymentAdapterKind.parseList(DeploymentAdapterKind.DEFAULT)));
    }

    @Test
    void aListEnablesSeveralAdaptersAndKeepsTheGivenOrder() {
        assertEquals(List.of(DeploymentAdapterKind.KUBERNETES, DeploymentAdapterKind.NOMAD),
                List.copyOf(DeploymentAdapterKind.parseList("kubernetes,nomad")));
    }

    @Test
    void caseAndSpacesAroundNamesAreIgnored() {
        assertEquals(List.of(DeploymentAdapterKind.NOMAD, DeploymentAdapterKind.KUBERNETES),
                List.copyOf(DeploymentAdapterKind.parseList("  Nomad , KUBERNETES ")));
    }

    @Test
    void anUnknownNameFailsAndListsTheAllowedOnes() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DeploymentAdapterKind.parseList("nomad,docker"));
        assertTrue(e.getMessage().contains("docker"), e.getMessage());
        assertTrue(e.getMessage().contains("nomad") && e.getMessage().contains("kubernetes"), e.getMessage());
    }

    @Test
    void aDuplicateIsRejectedNotSilentlyMerged() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DeploymentAdapterKind.parseList("nomad,Nomad"));
        assertTrue(e.getMessage().contains("deux fois"), e.getMessage());
    }

    @Test
    void anEmptyOrBlankListOrAnEmptyItemIsRejectedNotSilentlyDefaulted() {
        assertThrows(IllegalStateException.class, () -> DeploymentAdapterKind.parseList(""));
        assertThrows(IllegalStateException.class, () -> DeploymentAdapterKind.parseList("   "));
        assertThrows(IllegalStateException.class, () -> DeploymentAdapterKind.parseList(null));
        assertThrows(IllegalStateException.class, () -> DeploymentAdapterKind.parseList("nomad,"));
        assertThrows(IllegalStateException.class, () -> DeploymentAdapterKind.parseList(",nomad"));
    }

    @Test
    void propertyValuesMatchTheNamesUsedInManifestsAndMetrics() {
        assertEquals("nomad", DeploymentAdapterKind.NOMAD.propertyValue());
        assertEquals("kubernetes", DeploymentAdapterKind.KUBERNETES.propertyValue());
    }
}
