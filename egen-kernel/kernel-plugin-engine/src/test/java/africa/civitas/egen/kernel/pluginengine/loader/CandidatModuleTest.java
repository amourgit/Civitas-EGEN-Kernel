package africa.civitas.egen.kernel.pluginengine.loader;

import africa.civitas.egen.kernel.pluginengine.manifest.ManifestSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CandidatModuleTest {

    private static final ManifestSource UNE_SOURCE = () -> Map.of();

    @Test
    void rejectsANullPath() {
        assertThrows(NullPointerException.class, () -> new CandidatModule(null, UNE_SOURCE));
    }

    @Test
    void rejectsANullManifestSource() {
        assertThrows(NullPointerException.class, () -> new CandidatModule(Path.of("plugin.jar"), null));
    }
}
