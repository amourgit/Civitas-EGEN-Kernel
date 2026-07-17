package africa.civitas.egen.kernel.sdk.tracabilite;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActeurTest {

    @Test
    void factoryPersonneBuildsAValidActeur() {
        UUID id = UUID.randomUUID();
        Acteur acteur = Acteur.personne(id);

        assertEquals(ActeurType.PERSONNE, acteur.type());
        assertEquals(id, acteur.personneId());
        assertEquals(null, acteur.systemeLabel());
    }

    @Test
    void factorySystemeBuildsAValidActeur() {
        Acteur acteur = Acteur.systeme("synchronisation-keycloak");

        assertEquals(ActeurType.SYSTEME, acteur.type());
        assertEquals("synchronisation-keycloak", acteur.systemeLabel());
        assertEquals(null, acteur.personneId());
    }

    @Test
    void rejectsAPersonneActeurWithoutId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Acteur(ActeurType.PERSONNE, null, null));
    }

    @Test
    void rejectsAPersonneActeurCarryingASystemeLabel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Acteur(ActeurType.PERSONNE, UUID.randomUUID(), "should-not-be-here"));
    }

    @Test
    void rejectsASystemeActeurWithBlankLabel() {
        assertThrows(IllegalArgumentException.class,
                () -> new Acteur(ActeurType.SYSTEME, null, "  "));
    }

    @Test
    void rejectsASystemeActeurCarryingAPersonneId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Acteur(ActeurType.SYSTEME, UUID.randomUUID(), "systeme"));
    }
}
