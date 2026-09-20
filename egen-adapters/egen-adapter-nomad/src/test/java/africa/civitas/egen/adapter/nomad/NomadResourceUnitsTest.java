package africa.civitas.egen.adapter.nomad;

import africa.civitas.egen.application.port.DeploymentException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NomadResourceUnitsTest {

    @Test
    void parsesCpuWithMSuffixAsMhz() {
        assertEquals(500, NomadResourceUnits.parseCpuMhz("500m"));
    }

    @Test
    void parsesCpuWithoutSuffix() {
        assertEquals(1000, NomadResourceUnits.parseCpuMhz("1000"));
    }

    @Test
    void rejectsAnUnrecognizedCpuFormat() {
        assertThrows(DeploymentException.class, () -> NomadResourceUnits.parseCpuMhz("2vCPU"));
    }

    @Test
    void parsesMebibyteMemory() {
        assertEquals(512, NomadResourceUnits.parseMemoryMb("512Mi"));
    }

    @Test
    void parsesGibibyteMemoryAsMebibytes() {
        assertEquals(2048, NomadResourceUnits.parseMemoryMb("2Gi"));
    }

    @Test
    void rejectsAnUnrecognizedMemoryFormat() {
        assertThrows(DeploymentException.class, () -> NomadResourceUnits.parseMemoryMb("512"));
        assertThrows(DeploymentException.class, () -> NomadResourceUnits.parseMemoryMb("1TB"));
    }
}
