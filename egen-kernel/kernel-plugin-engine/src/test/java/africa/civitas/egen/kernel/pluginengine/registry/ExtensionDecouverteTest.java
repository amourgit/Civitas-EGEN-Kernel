package africa.civitas.egen.kernel.pluginengine.registry;

import africa.civitas.egen.kernel.pluginengine.testsupport.AutrePointExtensionDeTest;
import africa.civitas.egen.kernel.pluginengine.testsupport.ImplementationDeTest;
import africa.civitas.egen.kernel.pluginengine.testsupport.PointExtensionDeTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtensionDecouverteTest {

    @Test
    void acceptsAnInstanceThatActuallyImplementsTheDeclaredPoint() {
        ExtensionDecouverte decouverte = new ExtensionDecouverte(
                PointExtensionDeTest.class, new ImplementationDeTest("test"), 100, "academie");

        assertEquals("academie", decouverte.moduleId());
    }

    @Test
    void rejectsAnInstanceThatDoesNotImplementTheDeclaredPoint() {
        assertThrows(IllegalArgumentException.class, () -> new ExtensionDecouverte(
                AutrePointExtensionDeTest.class, new ImplementationDeTest("test"), 100, "academie"));
    }

    @Test
    void rejectsABlankModuleId() {
        assertThrows(IllegalArgumentException.class, () -> new ExtensionDecouverte(
                PointExtensionDeTest.class, new ImplementationDeTest("test"), 100, "  "));
    }

    @Test
    void rejectsANullPoint() {
        assertThrows(NullPointerException.class, () -> new ExtensionDecouverte(
                null, new ImplementationDeTest("test"), 100, "academie"));
    }

    @Test
    void rejectsANullInstance() {
        assertThrows(NullPointerException.class, () -> new ExtensionDecouverte(
                PointExtensionDeTest.class, null, 100, "academie"));
    }
}
