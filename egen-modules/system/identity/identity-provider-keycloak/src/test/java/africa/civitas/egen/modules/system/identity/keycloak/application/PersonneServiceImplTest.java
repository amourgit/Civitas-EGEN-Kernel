package africa.civitas.egen.modules.system.identity.keycloak.application;

import africa.civitas.egen.modules.system.identity.api.command.CreerPersonneCommand;
import africa.civitas.egen.modules.system.identity.api.domain.Personne;
import africa.civitas.egen.modules.system.identity.api.domain.StatutVital;
import africa.civitas.egen.modules.system.identity.api.exception.IdentiteConflitException;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PersonneServiceImplTest {

    @Inject
    PersonneServiceImpl service;

    private static CreerPersonneCommand commande(String identifiantCivilReference) {
        return new CreerPersonneCommand(
                identifiantCivilReference, "Mba", null, List.of("Samuel"),
                LocalDate.of(1990, 1, 1), "Libreville", null,
                List.of("GA"), List.of("fr"), StatutVital.VIVANT,
                null, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);
    }

    @Test
    @TestTransaction
    void creerPersistsAndReturnsTheNewPersonne() {
        Personne p = service.creer(commande("GA-TEST-001"));

        assertEquals("GA-TEST-001", p.identifiantCivilReference());
        assertEquals(1L, p.tracabilite().version());

        Optional<Personne> relue = service.trouverParId(p.id());
        assertTrue(relue.isPresent());
        assertEquals(p.id(), relue.get().id());
    }

    @Test
    @TestTransaction
    void creerGeneratesAnIdentifiantCivilReferenceWhenNoneProvided() {
        Personne p = service.creer(commande(null));

        assertTrue(p.identifiantCivilReference().startsWith("GEN-"));
    }

    @Test
    @TestTransaction
    void creerRejectsADuplicateIdentifiantCivilReference() {
        service.creer(commande("GA-TEST-DUP"));

        assertThrows(IdentiteConflitException.class, () -> service.creer(commande("GA-TEST-DUP")));
    }

    @Test
    @TestTransaction
    void trouverParIdReturnsEmptyWhenNotFound() {
        assertTrue(service.trouverParId(UUID.randomUUID()).isEmpty());
    }

    @Test
    @TestTransaction
    void trouverParIdentifiantCivilFindsTheRightPersonne() {
        Personne p = service.creer(commande("GA-TEST-002"));

        Optional<Personne> trouvee = service.trouverParIdentifiantCivil("GA-TEST-002");

        assertTrue(trouvee.isPresent());
        assertEquals(p.id(), trouvee.get().id());
    }
}
