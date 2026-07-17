package africa.civitas.egen.kernel.sdk.extension;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionAnnotationsTest {

    /** Point d'extension d'exemple, uniquement pour ce test. */
    interface SampleExtensionPoint extends ExtensionPoint {
        String greet();
    }

    /** Un second point d'extension, sans rapport, pour verifier la detection d'incoherence. */
    interface UnrelatedExtensionPoint extends ExtensionPoint {
        void doSomethingElse();
    }

    @Extension(point = SampleExtensionPoint.class, priority = 5)
    static class SampleExtensionImpl implements SampleExtensionPoint {
        @Override
        public String greet() {
            return "hello";
        }
    }

    @Module(id = "sample-module", name = "Module d'exemple", version = "1.0.0")
    static class SampleModuleDescriptor {
    }

    @Test
    void moduleAnnotationHasRuntimeRetentionAndTypeTarget() {
        Retention retention = Module.class.getAnnotation(Retention.class);
        Target target = Module.class.getAnnotation(Target.class);

        assertEquals(RetentionPolicy.RUNTIME, retention.value(),
                "Sans retention RUNTIME, le moteur de plugins ne pourrait pas lire @Module par reflexion.");
        assertArrayEquals(new ElementType[] {ElementType.TYPE}, target.value());
    }

    @Test
    void extensionAnnotationHasRuntimeRetentionAndTypeTarget() {
        Retention retention = Extension.class.getAnnotation(Retention.class);
        Target target = Extension.class.getAnnotation(Target.class);

        assertEquals(RetentionPolicy.RUNTIME, retention.value());
        assertArrayEquals(new ElementType[] {ElementType.TYPE}, target.value());
    }

    @Test
    void moduleDescriptorExposesDeclaredAttributes() {
        Module module = SampleModuleDescriptor.class.getAnnotation(Module.class);

        assertEquals("sample-module", module.id());
        assertEquals("Module d'exemple", module.name());
        assertEquals("1.0.0", module.version());
    }

    @Test
    void extensionDeclaresItsPointAndPriority() {
        Extension extension = SampleExtensionImpl.class.getAnnotation(Extension.class);

        assertEquals(SampleExtensionPoint.class, extension.point());
        assertEquals(5, extension.priority());
    }

    @Test
    void extensionDefaultPriorityIsOneHundred() {
        @Extension(point = SampleExtensionPoint.class)
        class DefaultPriorityExtension implements SampleExtensionPoint {
            @Override
            public String greet() {
                return "hi";
            }
        }

        Extension extension = DefaultPriorityExtension.class.getAnnotation(Extension.class);
        assertEquals(100, extension.priority());
    }

    /**
     * Simule exactement la verification de coherence que kernel-plugin-engine devra
     * effectuer au chargement d'un plugin : la classe annotee @Extension doit
     * reellement implementer le point d'extension qu'elle declare servir.
     */
    @Test
    void annotatedClassActuallyImplementsItsDeclaredExtensionPoint() {
        Extension extension = SampleExtensionImpl.class.getAnnotation(Extension.class);
        Class<? extends ExtensionPoint> declaredPoint = extension.point();

        assertTrue(declaredPoint.isAssignableFrom(SampleExtensionImpl.class),
                "La classe annotee doit implementer le point d'extension qu'elle declare.");
        assertFalse(UnrelatedExtensionPoint.class.isAssignableFrom(SampleExtensionImpl.class),
                "Verification negative de controle : l'implementation ne doit pas, par accident, "
                        + "satisfaire un point d'extension qu'elle n'a pas declare.");
    }
}
