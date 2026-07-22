package africa.civitas.egen.modules.system.identity.api.command;

import africa.civitas.egen.modules.system.identity.api.domain.TypeChangementIdentite;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnregistrerHistoriqueIdentiteCommandTest {

    private static final Acteur ACTEUR = Acteur.personne(UUID.randomUUID());

    @Test
    void acceptsAValidCommand() {
        assertDoesNotThrow(() -> new EnregistrerHistoriqueIdentiteCommand(
                UUID.randomUUID(), TypeChangementIdentite.CHANGEMENT_DE_NOM,
                "Mba", "Mba-Obame", null, LocalDate.now(), ACTEUR, OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    void rejectsIdenticalValues() {
        assertThrows(IllegalArgumentException.class, () -> new EnregistrerHistoriqueIdentiteCommand(
                UUID.randomUUID(), TypeChangementIdentite.CHANGEMENT_DE_NOM,
                "Mba", "Mba", null, LocalDate.now(), ACTEUR, OrigineDonnee.SAISIE_MANUELLE));
    }
}
