package africa.civitas.egen.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSpecTest {

    @Test
    void theBlockOfDoc06IsRepresentable() {
        EventSpec events = new EventSpec(
                List.of(new EventSpec.Published("africa.civitas.news.article.created.v1", "schemas/article-created.v1.json"),
                        new EventSpec.Published("africa.civitas.news.article.updated.v1", null)),
                List.of(new EventSpec.Consumed("africa.civitas.notification.delivery-failed.v1",
                        "news-service-consumers", new EventSpec.DeadLetter(5, "exponential-backoff"))));

        assertEquals(2, events.publishes().size());
        assertEquals(5, events.consumes().get(0).deadLetter().maxRetries());
        assertEquals(List.of("africa.civitas.news.article.created.v1", "africa.civitas.news.article.updated.v1",
                "africa.civitas.notification.delivery-failed.v1"), events.allTypes());
    }

    @Test
    void noEventsIsTheDefault() {
        assertTrue(EventSpec.none().publishes().isEmpty());
        assertTrue(new EventSpec(null, null).consumes().isEmpty());
        assertTrue(EventSpec.none().allTypes().isEmpty());
    }

    @Test
    void blankOptionalFieldsBecomeNull() {
        assertNull(new EventSpec.Published("a.b.v1", "  ").schemaRef());
        assertNull(new EventSpec.Consumed("a.b.v1", " ", null).subscriptionGroup());
    }

    @Test
    void aTypeBecomesABrokerSubjectSoItNeverCarriesSpacesOrWildcards() {
        for (String bad : new String[] {null, "", " ", "a b", "a.*", "a.>", "*", ">", ".a", "a/b", "a\nb", "é.v1"}) {
            assertThrows(IllegalArgumentException.class, () -> new EventSpec.Published(bad, null), String.valueOf(bad));
            assertThrows(IllegalArgumentException.class, () -> new EventSpec.Consumed(bad, null, null), String.valueOf(bad));
        }
        new EventSpec.Published("africa.civitas.news.article.created.v1", null);
        new EventSpec.Published("a_b-c.d9", null);
    }

    @Test
    void aTypeDeclaredTwiceIsRejectedInEachDirection() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new EventSpec(
                List.of(new EventSpec.Published("a.v1", null), new EventSpec.Published("a.v1", null)), List.of()))
                .getMessage().contains("events.publishes"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new EventSpec(List.of(),
                List.of(new EventSpec.Consumed("a.v1", null, null), new EventSpec.Consumed("a.v1", null, null))))
                .getMessage().contains("events.consumes"));
    }

    @Test
    void aServiceMayPublishAndConsumeTheSameType() {
        EventSpec events = new EventSpec(List.of(new EventSpec.Published("a.v1", null)),
                List.of(new EventSpec.Consumed("a.v1", null, null)));

        assertEquals(List.of("a.v1"), events.allTypes());
    }

    @Test
    void deadLetterIsValidated() {
        assertThrows(IllegalArgumentException.class, () -> new EventSpec.DeadLetter(-1, "fixed"));
        assertThrows(IllegalArgumentException.class, () -> new EventSpec.DeadLetter(3, " "));
        new EventSpec.DeadLetter(0, "fixed");
    }
}
