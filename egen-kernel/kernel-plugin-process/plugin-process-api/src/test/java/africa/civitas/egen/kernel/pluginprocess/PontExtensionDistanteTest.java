package africa.civitas.egen.kernel.pluginprocess;

import africa.civitas.egen.kernel.sdk.extension.ExtensionPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PontExtensionDistanteTest {

    /** Point d'extension de test — un point d'extension quelconque, comme le serait celui d'un vrai module metier. */
    private interface Salutation extends ExtensionPoint {
        String direBonjour(String nom);

        int compter(int a, int b);
    }

    @Test
    void theProxySatisfiesTheExtensionPointType() {
        AppelExtensionTransport transport = (point, methode, arguments) -> null;

        Salutation proxy = PontExtensionDistante.creerProxy(Salutation.class, transport);

        assertTrue(proxy instanceof ExtensionPoint);
        assertTrue(Salutation.class.isInstance(proxy));
    }

    @Test
    void eachMethodCallIsForwardedToTheTransportWithTheRightArguments() {
        AppelExtensionTransport transport = (point, methode, arguments) -> {
            assertEquals(Salutation.class, point);
            assertEquals("direBonjour", methode.getName());
            assertEquals(1, arguments.length);
            assertEquals("Kim", arguments[0]);
            return "Bonjour, " + arguments[0] + " !";
        };

        Salutation proxy = PontExtensionDistante.creerProxy(Salutation.class, transport);

        assertEquals("Bonjour, Kim !", proxy.direBonjour("Kim"));
    }

    @Test
    void theReturnValueFromTheTransportIsReturnedAsIs() {
        AppelExtensionTransport transport = (point, methode, arguments) -> (int) arguments[0] + (int) arguments[1];

        Salutation proxy = PontExtensionDistante.creerProxy(Salutation.class, transport);

        assertEquals(7, proxy.compter(3, 4));
    }

    @Test
    void aTransportFailureIsWrappedInAPluginProcessException() {
        AppelExtensionTransport transportEnEchec = (point, methode, arguments) -> {
            throw new RuntimeException("panne simulee du transport");
        };
        Salutation proxy = PontExtensionDistante.creerProxy(Salutation.class, transportEnEchec);

        PluginProcessException exception = assertThrows(PluginProcessException.class,
                () -> proxy.direBonjour("test"));
        assertTrue(exception.getMessage().contains("direBonjour"));
        assertTrue(exception.getCause().getMessage().contains("panne simulee"));
    }

    @Test
    void toStringWorksWithoutReachingTheTransport() {
        AppelExtensionTransport transportJamaisAppele = (point, methode, arguments) -> {
            throw new AssertionError("le transport ne doit jamais etre sollicite pour toString()");
        };

        Salutation proxy = PontExtensionDistante.creerProxy(Salutation.class, transportJamaisAppele);

        assertTrue(proxy.toString().contains("Salutation"));
    }

    @Test
    void equalsIsReflexiveWithoutReachingTheTransport() {
        AppelExtensionTransport transportJamaisAppele = (point, methode, arguments) -> {
            throw new AssertionError("le transport ne doit jamais etre sollicite pour equals()");
        };

        Salutation proxy = PontExtensionDistante.creerProxy(Salutation.class, transportJamaisAppele);

        assertTrue(proxy.equals(proxy));
    }

    @Test
    void hashCodeWorksWithoutReachingTheTransport() {
        AppelExtensionTransport transportJamaisAppele = (point, methode, arguments) -> {
            throw new AssertionError("le transport ne doit jamais etre sollicite pour hashCode()");
        };

        Salutation proxy = PontExtensionDistante.creerProxy(Salutation.class, transportJamaisAppele);

        proxy.hashCode();
    }

    @Test
    void rejectsANullExtensionPoint() {
        assertThrows(NullPointerException.class,
                () -> PontExtensionDistante.<Salutation>creerProxy(null, (point, methode, arguments) -> null));
    }

    @Test
    void rejectsANullTransport() {
        assertThrows(NullPointerException.class,
                () -> PontExtensionDistante.creerProxy(Salutation.class, null));
    }
}
