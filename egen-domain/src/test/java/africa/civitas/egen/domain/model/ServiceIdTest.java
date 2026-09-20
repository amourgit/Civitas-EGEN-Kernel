package africa.civitas.egen.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceIdTest {

    @Test
    void acceptsAValidLowercaseHyphenatedName() {
        ServiceId id = ServiceId.of("news-service");
        assertEquals("news-service", id.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "News-Service", "-news", "news-", "news_service", "news service"})
    void rejectsInvalidNames(String invalid) {
        assertThrows(ServiceId.InvalidServiceIdException.class, () -> ServiceId.of(invalid));
    }

    @Test
    void rejectsNull() {
        assertThrows(ServiceId.InvalidServiceIdException.class, () -> ServiceId.of(null));
    }
}
