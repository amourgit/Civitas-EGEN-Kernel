package africa.civitas.egen.kernel.identity.api.domain;

import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import africa.civitas.egen.kernel.sdk.tracabilite.Tracabilite;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoriqueIdentiteTest {

    private static final Tracabilite TRACABILITE =
            Tracabilite.initiale(Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);

    @Test
    void buildsAValidEntry() {
        HistoriqueIdentite h = new HistoriqueIdentite(
                UUID.randomUUID(), UUID.randomUUID(), TypeChangementIdentite.CHANGEMENT_DE_NOM,
                "Mba", "Mba-Obame", null, LocalDate.now(), TRACABILITE);

        assertTrue(h.pieceJustificative().isEmpty());
    }

    @Test
    void exposesThePieceJustificativeWhenPresent() {
        UUID piece = UUID.randomUUID();
        HistoriqueIdentite h = new HistoriqueIdentite(
                UUID.randomUUID(), UUID.randomUUID(), TypeChangementIdentite.CHANGEMENT_DE_NOM,
                "Mba", "Mba-Obame", piece, LocalDate.now(), TRACABILITE);

        assertTrue(h.pieceJustificative().isPresent());
    }

    @Test
    void rejectsIdenticalPrecedenteAndNouvelleValues() {
        assertThrows(IllegalArgumentException.class, () -> new HistoriqueIdentite(
                UUID.randomUUID(), UUID.randomUUID(), TypeChangementIdentite.CHANGEMENT_DE_NOM,
                "Mba", "Mba", null, LocalDate.now(), TRACABILITE));
    }
}
