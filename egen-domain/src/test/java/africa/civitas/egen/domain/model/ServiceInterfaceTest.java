package africa.civitas.egen.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceInterfaceTest {

    @Test
    void theShapeOfDoc21IsRepresentable() {
        ServiceInterface openapi = new ServiceInterface("openapi", "/internal/openapi.json", "read");
        assertEquals("openapi", openapi.type());
        assertEquals("/internal/openapi.json", openapi.ref());
        assertEquals("read", openapi.riskDefault());
    }

    @Test
    void riskDefaultIsOptional() {
        assertNull(new ServiceInterface("mcp", "mcp://news", null).riskDefault());
        assertNull(new ServiceInterface("mcp", "mcp://news", "  ").riskDefault());
    }

    @Test
    void valuesAreOpaque_noClosedListIsEnforced() {
        // Un type ou un niveau de risque inconnu AUJOURD'HUI doit etre accepte DEMAIN sans migrer le Kernel.
        ServiceInterface future = new ServiceInterface("grpc", "grpc://news:9090", "catastrophic");
        assertEquals("grpc", future.type());
        assertEquals("catastrophic", future.riskDefault());
    }

    @Test
    void onlyTheShapeIsChecked() {
        for (String bad : new String[] {null, "", "  "}) {
            assertThrows(IllegalArgumentException.class, () -> new ServiceInterface(bad, "ref", null));
            assertThrows(IllegalArgumentException.class, () -> new ServiceInterface("mcp", bad, null));
        }
    }

    @Test
    void aManifestCarriesTheDeclaredInterfaces() {
        List<ServiceInterface> declared = List.of(new ServiceInterface("openapi", "/openapi.json", "read"),
                new ServiceInterface("mcp", "mcp://news", "write"));
        ServiceManifest manifest = new ServiceManifestTest().manifestWith(declared);

        assertEquals(declared, manifest.interfaces());
        assertTrue(new ServiceManifestTest().manifestWith(null).interfaces().isEmpty());
    }
}
