package africa.civitas.egen.domain.model;

import africa.civitas.egen.domain.model.VersionConstraint.InvalidVersionConstraintException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Niveau 1 (docs 17) : aucune I/O. Contrat : 06, « Validation du manifeste », point 4. */
class VersionConstraintTest {

    private static ServiceVersion v(String raw) {
        return ServiceVersion.parse(raw);
    }

    @Test
    void theDocumentedExampleIsRejectedForAnOlderDependencyAndAcceptedOtherwise() {
        VersionConstraint constraint = VersionConstraint.parse(">=1.2.0");

        assertFalse(constraint.isSatisfiedBy(v("1.1.0"))); // « notification-service est a 1.1.0, >=1.2.0 requis »
        assertTrue(constraint.isSatisfiedBy(v("1.2.0")));
        assertTrue(constraint.isSatisfiedBy(v("2.0.0")));
    }

    @Test
    void eachOperatorComparesAgainstTheBoundary() {
        assertTrue(VersionConstraint.parse(">1.2.0").isSatisfiedBy(v("1.2.1")));
        assertFalse(VersionConstraint.parse(">1.2.0").isSatisfiedBy(v("1.2.0")));
        assertTrue(VersionConstraint.parse("<=1.2.0").isSatisfiedBy(v("1.2.0")));
        assertFalse(VersionConstraint.parse("<=1.2.0").isSatisfiedBy(v("1.2.1")));
        assertTrue(VersionConstraint.parse("<1.2.0").isSatisfiedBy(v("1.1.9")));
        assertFalse(VersionConstraint.parse("<1.2.0").isSatisfiedBy(v("1.2.0")));
        assertTrue(VersionConstraint.parse("=1.2.0").isSatisfiedBy(v("1.2.0")));
        assertTrue(VersionConstraint.parse("==1.2.0").isSatisfiedBy(v("1.2.0")));
        assertFalse(VersionConstraint.parse("==1.2.0").isSatisfiedBy(v("1.2.1")));
    }

    @Test
    void aBareVersionMeansEquality() {
        VersionConstraint constraint = VersionConstraint.parse("3.0.1");

        assertTrue(constraint.isSatisfiedBy(v("3.0.1")));
        assertFalse(constraint.isSatisfiedBy(v("3.0.2")));
    }

    @Test
    void commaSeparatedClausesMustAllHold() {
        VersionConstraint range = VersionConstraint.parse(">=1.2.0, <2.0.0");

        assertTrue(range.isSatisfiedBy(v("1.9.9")));
        assertFalse(range.isSatisfiedBy(v("2.0.0")));
        assertFalse(range.isSatisfiedBy(v("1.1.9")));
    }

    @Test
    void aPreReleaseSortsBeforeItsFinalVersion() {
        assertFalse(VersionConstraint.parse(">=1.2.0").isSatisfiedBy(v("1.2.0-rc.1")));
        assertTrue(VersionConstraint.parse("<1.2.0").isSatisfiedBy(v("1.2.0-rc.1")));
    }

    @Test
    void theCanonicalExpressionRoundTrips() {
        VersionConstraint parsed = VersionConstraint.parse("  >= 1.2.0 ,<2.0.0 ");

        assertEquals(">=1.2.0,<2.0.0", parsed.expression());
        assertEquals(parsed, VersionConstraint.parse(parsed.expression()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "latest", ">=", ">=1.2", "^1.2.0", "~1.2.0", "1.x", ">=1.2.0,", ",>=1.2.0",
            ">=1.2.0 <2.0.0", "=>1.2.0", "!=1.2.0", ">=v1.2.0"})
    void anUnreadableConstraintRefusesToBeBuilt(String raw) {
        assertThrows(InvalidVersionConstraintException.class, () -> VersionConstraint.parse(raw));
    }

    @Test
    void aNullConstraintIsRefused() {
        assertThrows(InvalidVersionConstraintException.class, () -> VersionConstraint.parse(null));
    }

    @Test
    void theErrorNamesTheOffendingConstraintAndTheExpectedFormat() {
        InvalidVersionConstraintException error =
                assertThrows(InvalidVersionConstraintException.class, () -> VersionConstraint.parse("^1.2.0"));

        assertTrue(error.getMessage().contains("^1.2.0"));
        assertTrue(error.getMessage().contains(">=1.2.0"));
    }

    @Test
    void aDependencyRefusesAnUnreadableConstraintAndExposesTheParsedOne() {
        ServiceId target = ServiceId.of("notification-service");

        assertThrows(InvalidVersionConstraintException.class, () -> new Dependency(target, "^1.2.0", true));

        Dependency dependency = new Dependency(target, ">=1.2.0", true);
        assertEquals(">=1.2.0", dependency.versionConstraint()); // restituee telle que declaree
        assertTrue(dependency.constraint().isSatisfiedBy(v("1.2.0")));
    }
}
