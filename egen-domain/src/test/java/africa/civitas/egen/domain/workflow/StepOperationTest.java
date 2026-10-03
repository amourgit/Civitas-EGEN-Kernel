package africa.civitas.egen.domain.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StepOperationTest {

    @Test
    void parsesAnHttpOperation() {
        StepOperation operation = StepOperation.parse("POST /internal/validate");

        StepOperation.Http http = assertInstanceOf(StepOperation.Http.class, operation);
        assertEquals("POST", http.method());
        assertEquals("/internal/validate", http.path());
        assertEquals("POST /internal/validate", operation.raw());
    }

    @Test
    void parsesAnEventOperation() {
        StepOperation operation = StepOperation.parse("EVENT africa.civitas.news.article.published.v1");

        StepOperation.Event event = assertInstanceOf(StepOperation.Event.class, operation);
        assertEquals("africa.civitas.news.article.published.v1", event.eventType());
        assertEquals("EVENT africa.civitas.news.article.published.v1", operation.raw());
    }

    @Test
    void lowercaseHttpMethodIsNormalizedToUppercase() {
        StepOperation.Http http = (StepOperation.Http) StepOperation.parse("post /internal/validate");
        assertEquals("POST", http.method());
    }

    @Test
    void rejectsAnUnknownMethod() {
        assertThrows(IllegalArgumentException.class, () -> StepOperation.parse("FETCH /internal/validate"));
    }

    @Test
    void rejectsAPathNotStartingWithASlash() {
        assertThrows(IllegalArgumentException.class, () -> new StepOperation.Http("POST", "internal/validate"));
    }

    @Test
    void rejectsABlankEventType() {
        assertThrows(IllegalArgumentException.class, () -> new StepOperation.Event("  "));
    }

    @Test
    void rejectsAnUnrecognizedForm() {
        assertThrows(IllegalArgumentException.class, () -> StepOperation.parse("just some text"));
    }

    @Test
    void rejectsABlankOperation() {
        assertThrows(IllegalArgumentException.class, () -> StepOperation.parse(" "));
    }
}
