package africa.civitas.egen.sidecar;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidecarConfigTest {

    @Test
    void theControlPlaneUrlIsMandatoryAndTheDefaultsApply() {
        SidecarConfig config = SidecarConfig.fromEnvironment(Map.of("EGEN_CONTROL_PLANE_URL", " http://egen:8080 "));

        assertEquals("http://egen:8080", config.controlPlaneUrl());
        assertNull(config.token());
        assertEquals(3500, config.port());
    }

    @Test
    void tokenAndPortAreReadFromTheEnvironment() {
        SidecarConfig config = SidecarConfig.fromEnvironment(Map.of("EGEN_CONTROL_PLANE_URL", "http://egen:8080",
                "EGEN_TOKEN", "abc", "EGEN_SIDECAR_PORT", "4000"));

        assertEquals("abc", config.token());
        assertEquals(4000, config.port());
    }

    @Test
    void invalidConfigurationIsRejectedWithAMessageNamingTheVariable() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> SidecarConfig.fromEnvironment(Map.of()))
                .getMessage().contains("EGEN_CONTROL_PLANE_URL"));
        for (String badPort : new String[] {"abc", "0", "70000", "-1"}) {
            assertTrue(assertThrows(IllegalArgumentException.class, () -> SidecarConfig.fromEnvironment(
                    Map.of("EGEN_CONTROL_PLANE_URL", "http://x", "EGEN_SIDECAR_PORT", badPort)))
                    .getMessage().contains("EGEN_SIDECAR_PORT"), badPort);
        }
    }

    @Test
    void aBlankTokenCountsAsNoToken() {
        assertNull(SidecarConfig.fromEnvironment(Map.of("EGEN_CONTROL_PLANE_URL", "http://x", "EGEN_TOKEN", "  ")).token());
    }
}
