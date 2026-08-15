package africa.civitas.egen.kernel.sdk.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventTypeTest {

    @Test
    void acceptsAWellFormedHierarchicalName() {
        EventType type = new EventType("identite.personne.creee");

        assertEquals("identite.personne.creee", type.name());
        assertEquals("identite", type.systemeOrigine());
    }

    @Test
    void acceptsKebabCaseSegments() {
        EventType type = new EventType("reconnaissance-faciale.evenement.detecte");

        assertEquals("reconnaissance-faciale", type.systemeOrigine());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void rejectsBlankNames(String blank) {
        assertThrows(IllegalArgumentException.class, () -> new EventType(blank));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SansPoint",
            "Identite.Personne.Creee",
            "identite..creee",
            ".identite.creee",
            "identite.creee.",
            "identite",
            "1identite.personne"
    })
    void rejectsNamesThatDoNotFollowTheHierarchicalConvention(String malformed) {
        assertThrows(IllegalArgumentException.class, () -> new EventType(malformed));
    }
}
