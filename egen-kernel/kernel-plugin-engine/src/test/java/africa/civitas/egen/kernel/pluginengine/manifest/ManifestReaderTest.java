package africa.civitas.egen.kernel.pluginengine.manifest;

import africa.civitas.egen.kernel.sdk.manifest.ManifestValidationException;
import africa.civitas.egen.kernel.sdk.manifest.ManifesteExtension;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestReaderTest {

    private final ManifestReader reader = new ManifestReader();

    private static ManifestSource source(Map<String, String> donnees) {
        return () -> donnees;
    }

    private static Map<String, String> donneesMinimales() {
        Map<String, String> donnees = new HashMap<>();
        donnees.put("moduleId", "academie");
        donnees.put("version", "1.0.0");
        return donnees;
    }

    @Test
    void readsAMinimalManifestWithOnlyTheRequiredFields() {
        ManifesteExtension manifeste = reader.lire(source(donneesMinimales()));

        assertEquals("academie", manifeste.moduleId());
        assertEquals("1.0.0", manifeste.version());
        assertTrue(manifeste.dependencies().isEmpty());
    }

    @Test
    void readsAllOptionalListFieldsAsCommaSeparatedValues() {
        Map<String, String> donnees = donneesMinimales();
        donnees.put("eventsEmitted", "academie.cours.cree");
        donnees.put("eventsConsumed", "identite.personne.creee");
        donnees.put("resourceTypesProvided", "videoprojecteur");
        donnees.put("dependencies", "identite,notifications");

        ManifesteExtension manifeste = reader.lire(source(donnees));

        assertEquals(List.of("academie.cours.cree"), manifeste.eventsEmitted());
        assertEquals(List.of("identite.personne.creee"), manifeste.eventsConsumed());
        assertEquals(List.of("videoprojecteur"), manifeste.resourceTypesProvided());
        assertEquals(List.of("identite", "notifications"), manifeste.dependencies());
    }

    @Test
    void rejectsDataMissingTheModuleId() {
        Map<String, String> donnees = new HashMap<>();
        donnees.put("version", "1.0.0");

        assertThrows(ManifestReadException.class, () -> reader.lire(source(donnees)));
    }

    @Test
    void rejectsDataMissingTheVersion() {
        Map<String, String> donnees = new HashMap<>();
        donnees.put("moduleId", "academie");

        assertThrows(ManifestReadException.class, () -> reader.lire(source(donnees)));
    }

    @Test
    void rejectsABlankModuleId() {
        Map<String, String> donnees = donneesMinimales();
        donnees.put("moduleId", "   ");

        assertThrows(ManifestReadException.class, () -> reader.lire(source(donnees)));
    }

    @Test
    void delegatesStructuralValidationToManifesteExtensionItself() {
        Map<String, String> donnees = donneesMinimales();
        donnees.put("moduleId", "Academie_Invalide");

        assertThrows(ManifestValidationException.class, () -> reader.lire(source(donnees)));
    }

    @Test
    void rejectsANonSemanticVersion() {
        Map<String, String> donnees = donneesMinimales();
        donnees.put("version", "pas-une-version");

        assertThrows(ManifestValidationException.class, () -> reader.lire(source(donnees)));
    }

    @Test
    void propagatesFailuresFromTheSourceItself() {
        ManifestSource sourceEnEchec = () -> {
            throw new ManifestReadException("fichier absent");
        };

        assertThrows(ManifestReadException.class, () -> reader.lire(sourceEnEchec));
    }

    @Test
    void rejectsANullSource() {
        assertThrows(IllegalArgumentException.class, () -> reader.lire(null));
    }
}
