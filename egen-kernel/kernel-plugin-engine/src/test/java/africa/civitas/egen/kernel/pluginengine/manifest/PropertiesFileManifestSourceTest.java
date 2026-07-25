package africa.civitas.egen.kernel.pluginengine.manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PropertiesFileManifestSourceTest {

    @TempDir
    Path repertoireTemporaire;

    @Test
    void readsAllKeysFromARealPropertiesFile() throws IOException {
        Path fichier = repertoireTemporaire.resolve("plugin.properties");
        Files.writeString(fichier,
                "moduleId=academie\nversion=1.0.0\ndependencies=identite,organisationnel\n",
                StandardCharsets.UTF_8);

        Map<String, String> donnees = new PropertiesFileManifestSource(fichier).donneesBrutes();

        assertEquals("academie", donnees.get("moduleId"));
        assertEquals("1.0.0", donnees.get("version"));
        assertEquals("identite,organisationnel", donnees.get("dependencies"));
    }

    @Test
    void rejectsAMissingFile() {
        Path fichierAbsent = repertoireTemporaire.resolve("absent.properties");

        assertThrows(ManifestReadException.class,
                () -> new PropertiesFileManifestSource(fichierAbsent).donneesBrutes());
    }

    @Test
    void rejectsADirectoryGivenInPlaceOfAFile() {
        assertThrows(ManifestReadException.class,
                () -> new PropertiesFileManifestSource(repertoireTemporaire).donneesBrutes());
    }

    @Test
    void rejectsANullPath() {
        assertThrows(IllegalArgumentException.class, () -> new PropertiesFileManifestSource(null));
    }
}
