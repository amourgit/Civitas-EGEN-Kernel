package africa.civitas.egen.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceVersionTest {

    @Test
    void parsesAPlainSemver() {
        ServiceVersion v = ServiceVersion.parse("2.4.0");
        assertEquals(2, v.major());
        assertEquals(4, v.minor());
        assertEquals(0, v.patch());
        assertFalse(v.isPreRelease());
        assertEquals("2.4.0", v.toString());
    }

    @Test
    void parsesAPreReleaseSemver() {
        ServiceVersion v = ServiceVersion.parse("2.4.0-rc.1");
        assertTrue(v.isPreRelease());
        assertEquals("2.4.0-rc.1", v.toString());
    }

    @Test
    void rejectsNonSemverStrings() {
        assertThrows(ServiceVersion.InvalidServiceVersionException.class,
                () -> ServiceVersion.parse("2.4"));
        assertThrows(ServiceVersion.InvalidServiceVersionException.class,
                () -> ServiceVersion.parse("v2.4.0"));
        assertThrows(ServiceVersion.InvalidServiceVersionException.class,
                () -> ServiceVersion.parse(""));
    }

    @Test
    void ordersByPrecedenceAndTreatsPreReleaseAsLowerThanRelease() {
        ServiceVersion v240 = ServiceVersion.parse("2.4.0");
        ServiceVersion v241 = ServiceVersion.parse("2.4.1");
        ServiceVersion v240rc = ServiceVersion.parse("2.4.0-rc.1");

        assertTrue(v240.compareTo(v241) < 0);
        assertTrue(v240rc.compareTo(v240) < 0);
    }
}
