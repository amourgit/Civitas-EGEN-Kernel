package africa.civitas.egen.application.workflow;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonObjectsTest {

    @Test
    void emptyMapIsAnEmptyObject() {
        assertEquals("{}", JsonObjects.toJson(Map.of()));
    }

    @Test
    void keysAreSortedForADeterministicOutput() {
        assertEquals("{\"a\":\"1\",\"b\":\"2\",\"c\":\"3\"}", JsonObjects.toJson(Map.of("c", "3", "a", "1", "b", "2")));
    }

    @Test
    void quotesBackslashesAndControlCharactersAreEscaped() {
        assertEquals("{\"k\":\"a\\\"b\\\\c\\nd\\te\\u0001\"}", JsonObjects.toJson(Map.of("k", "a\"b\\c\nd\te\u0001")));
    }

    @Test
    void nonAsciiTextIsKeptAsIs() {
        assertEquals("{\"titre\":\"Libreville — été\"}", JsonObjects.toJson(Map.of("titre", "Libreville — été")));
    }
}
