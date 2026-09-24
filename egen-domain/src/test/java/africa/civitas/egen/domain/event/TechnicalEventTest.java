package africa.civitas.egen.domain.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TechnicalEventTest {

    @Test
    void createBuildsARootEventWithNoCausationId() {
        TechnicalEvent event = TechnicalEvent.create("/news-service",
                "africa.civitas.news.article.created.v1", "corr-1", "{\"articleId\":\"art-1\"}");

        assertNotNull(event.id());
        assertEquals("1.0", event.specVersion());
        assertNull(event.causationId());
        assertEquals("corr-1", event.correlationId());
    }

    @Test
    void causedByPropagatesTheSameCorrelationId() {
        TechnicalEvent root = TechnicalEvent.create("/news-service", "article.created", "corr-1", "{}");
        TechnicalEvent caused = root.causedBy(root.id(), "/notification-service",
                "notification.sent", "{}");

        assertEquals(root.correlationId(), caused.correlationId());
        assertEquals(root.id(), caused.causationId());
    }

    @Test
    void rejectsABlankType() {
        assertThrows(IllegalArgumentException.class,
                () -> TechnicalEvent.create("/news-service", "", "corr-1", "{}"));
    }

    @Test
    void rejectsNullData() {
        assertThrows(IllegalArgumentException.class,
                () -> TechnicalEvent.create("/news-service", "article.created", "corr-1", null));
    }
}
