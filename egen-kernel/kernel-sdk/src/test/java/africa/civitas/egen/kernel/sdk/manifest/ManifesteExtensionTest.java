package africa.civitas.egen.kernel.sdk.manifest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifesteExtensionTest {

    @Test
    void buildsAValidMinimalManifestWithNoOptionalLists() {
        ManifesteExtension manifest = ManifesteExtension.builder("academie", "1.0.0").build();

        assertEquals("academie", manifest.moduleId());
        assertEquals("1.0.0", manifest.version());
        assertTrue(manifest.cellTypesProvided().isEmpty());
        assertTrue(manifest.dependencies().isEmpty());
    }

    @Test
    void buildsARichManifestWithAllDeclarationsPopulated() {
        ManifesteExtension manifest = ManifesteExtension.builder("academie", "2.3.1")
                .cellType("Faculte")
                .cellType("Departement")
                .mandate("Enseignant")
                .emits("academie.cours.cree")
                .consumes("organisation.affectation.terminee")
                .resourceType("Salle de classe")
                .dependsOn("identite")
                .dependsOn("organisationnel")
                .build();

        assertEquals(List.of("Faculte", "Departement"), manifest.cellTypesProvided());
        assertEquals(List.of("Enseignant"), manifest.mandatesProvided());
        assertEquals(List.of("academie.cours.cree"), manifest.eventsEmitted());
        assertEquals(List.of("organisation.affectation.terminee"), manifest.eventsConsumed());
        assertEquals(List.of("Salle de classe"), manifest.resourceTypesProvided());
        assertEquals(List.of("identite", "organisationnel"), manifest.dependencies());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Academie", "academie_rh", "-academie", "academie-", "1academie"})
    void rejectsAMalformedModuleId(String badId) {
        assertThrows(ManifestValidationException.class,
                () -> ManifesteExtension.builder(badId, "1.0.0").build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1.0", "1", "v1.0.0", "1.0.0.0", "1.a.0"})
    void rejectsANonSemanticVersion(String badVersion) {
        assertThrows(ManifestValidationException.class,
                () -> ManifesteExtension.builder("academie", badVersion).build());
    }

    @Test
    void acceptsASemanticVersionWithPreReleaseSuffix() {
        ManifesteExtension manifest = ManifesteExtension.builder("academie", "1.0.0-beta.1").build();

        assertEquals("1.0.0-beta.1", manifest.version());
    }

    @Test
    void rejectsABlankEntryInAnyDeclarationList() {
        ManifestValidationException exception = assertThrows(ManifestValidationException.class,
                () -> ManifesteExtension.builder("academie", "1.0.0")
                        .cellType("Faculte")
                        .cellType("  ")
                        .build());

        assertTrue(exception.getMessage().contains("cellTypesProvided"));
    }

    @Test
    void rejectsADuplicateEntryInTheSameDeclarationList() {
        ManifestValidationException exception = assertThrows(ManifestValidationException.class,
                () -> ManifesteExtension.builder("academie", "1.0.0")
                        .emits("academie.cours.cree")
                        .emits("academie.cours.cree")
                        .build());

        assertTrue(exception.getMessage().contains("eventsEmitted"));
    }

    @Test
    void rejectsAModuleDeclaringItselfAsADependency() {
        assertThrows(ManifestValidationException.class,
                () -> ManifesteExtension.builder("academie", "1.0.0")
                        .dependsOn("identite")
                        .dependsOn("academie")
                        .build());
    }

    @Test
    void declarationListsAreUnmodifiable() {
        ManifesteExtension manifest = ManifesteExtension.builder("academie", "1.0.0")
                .cellType("Faculte")
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> manifest.cellTypesProvided().add("Departement"));
    }

    @Test
    void twoManifestsWithTheSameDeclarationsAreEqual() {
        ManifesteExtension first = ManifesteExtension.builder("academie", "1.0.0")
                .cellType("Faculte")
                .build();
        ManifesteExtension second = ManifesteExtension.builder("academie", "1.0.0")
                .cellType("Faculte")
                .build();

        assertEquals(first, second, "ManifesteExtension est un record : l'egalite doit rester "
                + "structurelle, pas basee sur l'identite d'objet.");
    }
}
