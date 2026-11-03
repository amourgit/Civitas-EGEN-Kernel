package africa.civitas.egen.adapter.kubernetes;

import africa.civitas.egen.application.port.DeploymentException;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Niveau 1 (docs/architecture/17) : quantites de ressources et configuration, sans reseau. */
class KubernetesConfigAndQuantitiesTest {

    @Test
    void cpuAcceptsIntegerAndMilliCores() {
        assertEquals("500m", KubernetesResourceQuantities.cpu("500m"));
        assertEquals("2", KubernetesResourceQuantities.cpu(" 2 "));
    }

    @Test
    void cpuRejectsMalformedValuesBeforeAnyNetworkCall() {
        for (String bad : new String[] {"0.5", "500M", "abc", "m", "-1", "1 core"}) {
            assertThrows(DeploymentException.class, () -> KubernetesResourceQuantities.cpu(bad), bad);
        }
    }

    @Test
    void memoryAcceptsIntegerAndBinarySuffixes() {
        assertEquals("512Mi", KubernetesResourceQuantities.memory("512Mi"));
        assertEquals("1Gi", KubernetesResourceQuantities.memory("1Gi"));
        assertEquals("64Ki", KubernetesResourceQuantities.memory("64Ki"));
        assertEquals("2Ti", KubernetesResourceQuantities.memory("2Ti"));
        assertEquals("1048576", KubernetesResourceQuantities.memory("1048576"));
    }

    @Test
    void memoryRejectsMalformedValues() {
        for (String bad : new String[] {"512MB", "512mi", "1.5Gi", "Gi", "", "-5Mi"}) {
            assertThrows(DeploymentException.class, () -> KubernetesResourceQuantities.memory(bad), bad);
        }
    }

    @Test
    void configRejectsInvalidNamespaceAndPort() {
        URI api = URI.create("https://k8s.example:6443");
        assertThrows(IllegalArgumentException.class, () -> new KubernetesConfig(api, "Bad_Namespace", null, null, 8080));
        assertThrows(IllegalArgumentException.class, () -> new KubernetesConfig(api, "", null, null, 8080));
        assertThrows(IllegalArgumentException.class, () -> new KubernetesConfig(api, "egen", null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new KubernetesConfig(api, "egen", null, null, 70000));
        assertThrows(IllegalArgumentException.class, () -> new KubernetesConfig(null, "egen", null, null, 8080));
    }

    @Test
    void configToStringNeverLeaksTheToken() {
        KubernetesConfig config = new KubernetesConfig(URI.create("https://k8s.example:6443"), "egen",
                "secret-token-value", null, 8080);
        assertFalse(config.toString().contains("secret-token-value"));
        assertTrue(config.toString().contains("***"));
    }

    @Test
    void allocationStatusMapsEveryPodPhase() {
        assertEquals("running", KubernetesDeploymentAdapter.allocationStatus("Running"));
        assertEquals("pending", KubernetesDeploymentAdapter.allocationStatus("Pending"));
        assertEquals("complete", KubernetesDeploymentAdapter.allocationStatus("Succeeded"));
        assertEquals("failed", KubernetesDeploymentAdapter.allocationStatus("Failed"));
        assertEquals("lost", KubernetesDeploymentAdapter.allocationStatus("Unknown"));
        assertEquals("unknown", KubernetesDeploymentAdapter.allocationStatus("Whatever"));
        assertEquals("unknown", KubernetesDeploymentAdapter.allocationStatus(""));
    }
}
