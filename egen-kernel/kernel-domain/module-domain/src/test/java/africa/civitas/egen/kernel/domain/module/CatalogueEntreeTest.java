package africa.civitas.egen.kernel.domain.module;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogueEntreeTest {

    private static final Tracabilite UNE_TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void rejectsABlankName() {
        assertThrows(ModuleDomainException.class, () -> new CatalogueEntree(
                UUID.randomUUID(), new ModuleId("academie"), "  ", "description", UNE_TRACABILITE));
    }

    @Test
    void rejectsANullModuleId() {
        assertThrows(NullPointerException.class, () -> new CatalogueEntree(
                UUID.randomUUID(), null, "Academie", "description", UNE_TRACABILITE));
    }

    @Test
    void rejectsANullTracabilite() {
        assertThrows(NullPointerException.class, () -> new CatalogueEntree(
                UUID.randomUUID(), new ModuleId("academie"), "Academie", "description", null));
    }
}
