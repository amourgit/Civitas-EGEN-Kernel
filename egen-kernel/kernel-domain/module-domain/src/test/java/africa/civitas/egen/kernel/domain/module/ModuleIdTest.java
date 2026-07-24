package africa.civitas.egen.kernel.domain.module;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"academie", "reconnaissance-faciale", "rh2", "a-b-c-d"})
    void acceptsValidKebabCaseIdentifiers(String valeur) {
        assertEquals(valeur, new ModuleId(valeur).valeur());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Academie", "reconnaissance_faciale", "-academie", "academie-", "", " "})
    void rejectsInvalidIdentifiers(String valeur) {
        assertThrows(ModuleDomainException.class, () -> new ModuleId(valeur));
    }

    @Test
    void rejectsANullValue() {
        assertThrows(ModuleDomainException.class, () -> new ModuleId(null));
    }

    @Test
    void toStringReturnsTheRawValue() {
        assertEquals("academie", new ModuleId("academie").toString());
    }
}
