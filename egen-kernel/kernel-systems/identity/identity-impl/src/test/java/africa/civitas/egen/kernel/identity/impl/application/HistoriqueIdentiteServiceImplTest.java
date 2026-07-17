package africa.civitas.egen.kernel.identity.impl.application;

import africa.civitas.egen.kernel.identity.api.command.CreerPersonneCommand;
import africa.civitas.egen.kernel.identity.api.command.EnregistrerHistoriqueIdentiteCommand;
import africa.civitas.egen.kernel.identity.api.domain.HistoriqueIdentite;
import africa.civitas.egen.kernel.identity.api.domain.Personne;
import africa.civitas.egen.kernel.identity.api.domain.StatutVital;
import africa.civitas.egen.kernel.identity.api.domain.TypeChangementIdentite;
import africa.civitas.egen.kernel.identity.api.exception.PersonneIntrouvableException;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class HistoriqueIdentiteServiceImplTest {

    @Inject
    PersonneServiceImpl personneService;

    @Inject
    HistoriqueIdentiteServiceImpl historiqueService;

    private Personne unePersonne(String ref) {
        return personneService.creer(new CreerPersonneCommand(
                ref, "Mba", null, List.of("Samuel"), LocalDate.of(1990, 1, 1),
                null, null, List.of("GA"), List.of(), StatutVital.VIVANT,
                null, null, null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void enregistrerPersistsAndReturnsTheEntry() {
        Personne p = unePersonne("GA-HIST-001");

        HistoriqueIdentite h = historiqueService.enregistrer(new EnregistrerHistoriqueIdentiteCommand(
                p.id(), TypeChangementIdentite.CHANGEMENT_DE_NOM, "Mba", "Mba-Obame",
                null, LocalDate.now(), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(p.id(), h.personneId());
        assertEquals("Mba-Obame", h.valeurNouvelle());
    }

    @Test
    @TestTransaction
    void enregistrerRejectsAnUnknownPersonne() {
        assertThrows(PersonneIntrouvableException.class, () -> historiqueService.enregistrer(
                new EnregistrerHistoriqueIdentiteCommand(
                        UUID.randomUUID(), TypeChangementIdentite.CHANGEMENT_DE_NOM, "Mba", "Mba-Obame",
                        null, LocalDate.now(), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void pourPersonneReturnsEntriesInChronologicalOrder() {
        Personne p = unePersonne("GA-HIST-002");
        historiqueService.enregistrer(new EnregistrerHistoriqueIdentiteCommand(
                p.id(), TypeChangementIdentite.CHANGEMENT_DE_NOM, "Mba", "Mba-Obame",
                null, LocalDate.of(2020, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        historiqueService.enregistrer(new EnregistrerHistoriqueIdentiteCommand(
                p.id(), TypeChangementIdentite.CORRECTION_ADMINISTRATIVE, "Mba-Obame", "Mba-Obame Jr",
                null, LocalDate.of(2021, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        List<HistoriqueIdentite> historique = historiqueService.pourPersonne(p.id());

        assertEquals(2, historique.size());
        assertEquals("Mba-Obame", historique.get(0).valeurNouvelle());
        assertEquals("Mba-Obame Jr", historique.get(1).valeurNouvelle());
    }
}
