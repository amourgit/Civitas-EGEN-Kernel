package africa.civitas.egen.kernel.pluginengine.registry;

import africa.civitas.egen.kernel.pluginengine.testsupport.ImplementationDeTest;
import africa.civitas.egen.kernel.pluginengine.testsupport.PointExtensionDeTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionRegistryTest {

    private final ExtensionRegistry registry = new ExtensionRegistry();

    @Test
    void obtenirReturnsAnEmptyListWhenNoExtensionServesThePoint() {
        assertTrue(registry.obtenir(PointExtensionDeTest.class).isEmpty());
    }

    @Test
    void aRegisteredExtensionBecomesRetrievableByItsPoint() {
        registry.enregistrer(new ExtensionDecouverte(
                PointExtensionDeTest.class, new ImplementationDeTest("A"), 100, "module-a"));

        List<PointExtensionDeTest> extensions = registry.obtenir(PointExtensionDeTest.class);

        assertEquals(1, extensions.size());
        assertEquals("A", extensions.get(0).nom());
    }

    @Test
    void multipleExtensionsForTheSamePointAreOrderedByAscendingPriority() {
        registry.enregistrer(new ExtensionDecouverte(
                PointExtensionDeTest.class, new ImplementationDeTest("basse-priorite"), 200, "module-a"));
        registry.enregistrer(new ExtensionDecouverte(
                PointExtensionDeTest.class, new ImplementationDeTest("haute-priorite"), 10, "module-b"));

        List<PointExtensionDeTest> extensions = registry.obtenir(PointExtensionDeTest.class);

        assertEquals("haute-priorite", extensions.get(0).nom());
        assertEquals("basse-priorite", extensions.get(1).nom());
    }

    @Test
    void enregistrerToutesRegistersEveryEntryInOneCall() {
        registry.enregistrerToutes(List.of(
                new ExtensionDecouverte(PointExtensionDeTest.class, new ImplementationDeTest("A"), 100, "module-a"),
                new ExtensionDecouverte(PointExtensionDeTest.class, new ImplementationDeTest("B"), 100, "module-a")));

        assertEquals(2, registry.obtenir(PointExtensionDeTest.class).size());
    }

    @Test
    void desenregistrerToutPourRemovesOnlyTheExtensionsOfThatModule() {
        registry.enregistrer(new ExtensionDecouverte(
                PointExtensionDeTest.class, new ImplementationDeTest("de-a"), 100, "module-a"));
        registry.enregistrer(new ExtensionDecouverte(
                PointExtensionDeTest.class, new ImplementationDeTest("de-b"), 100, "module-b"));

        int retirees = registry.desenregistrerToutPour("module-a");

        assertEquals(1, retirees);
        List<PointExtensionDeTest> restantes = registry.obtenir(PointExtensionDeTest.class);
        assertEquals(1, restantes.size());
        assertEquals("de-b", restantes.get(0).nom());
    }

    @Test
    void desenregistrerToutPourAnUnknownModuleRemovesNothing() {
        registry.enregistrer(new ExtensionDecouverte(
                PointExtensionDeTest.class, new ImplementationDeTest("A"), 100, "module-a"));

        int retirees = registry.desenregistrerToutPour("module-inconnu");

        assertEquals(0, retirees);
        assertEquals(1, registry.obtenir(PointExtensionDeTest.class).size());
    }

    @Test
    void countersReflectRegistrationsAcrossDistinctPoints() {
        registry.enregistrer(new ExtensionDecouverte(
                PointExtensionDeTest.class, new ImplementationDeTest("A"), 100, "module-a"));

        assertEquals(1, registry.nombreDePointsServis());
        assertEquals(1, registry.nombreTotalDeExtensions());
    }

    @Test
    void rejectsANullPointOnLookup() {
        assertThrows(IllegalArgumentException.class, () -> registry.obtenir(null));
    }

    @Test
    void rejectsABlankModuleIdOnUnregister() {
        assertThrows(IllegalArgumentException.class, () -> registry.desenregistrerToutPour(" "));
    }
}
