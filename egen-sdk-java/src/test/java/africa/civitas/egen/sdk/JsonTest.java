package africa.civitas.egen.sdk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {

    @Test
    void parsesObjectsArraysAndScalars() {
        Map<String, Object> root = Json.parseObject(
                "{\"a\": 1, \"b\": [true, false, null, \"x\"], \"c\": {\"d\": -2.5e1}, \"e\": \"\"}");

        assertEquals(1L, root.get("a"));
        assertEquals(List.of(true, false), ((List<?>) root.get("b")).subList(0, 2));
        assertNull(((List<?>) root.get("b")).get(2));
        assertEquals("x", ((List<?>) root.get("b")).get(3));
        assertEquals(-25.0, ((Map<?, ?>) root.get("c")).get("d"));
        assertEquals("", root.get("e"));
    }

    @Test
    void decodesEscapesIncludingUnicode() {
        assertEquals("a\"b\\c/d\ne\t\u00e9", Json.parse("\"a\\\"b\\\\c\\/d\\ne\\t\\u00e9\""));
    }

    @Test
    void keepsNonAsciiTextAsIs() {
        assertEquals("Libreville \u2014 \u00e9t\u00e9", Json.parse("\"Libreville \u2014 \u00e9t\u00e9\""));
    }

    @Test
    void emptyContainers() {
        assertEquals(Map.of(), Json.parse("{ }"));
        assertEquals(List.of(), Json.parse("[ ]"));
    }

    @Test
    void rejectsInvalidDocuments() {
        for (String bad : new String[] {"", "{", "[1,]", "{\"a\":}", "{\"a\" 1}", "{a:1}", "tru", "\"abc",
                "1 2", "{\"a\":1} x", "[1 2]", "\"\\q\"", "\"\\u12\"", "-", "{\"a\":1,}"}) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(bad), "devrait etre refuse : " + bad);
        }
    }

    @Test
    void parseObjectRefusesANonObjectRoot() {
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("[1]"));
    }
}
