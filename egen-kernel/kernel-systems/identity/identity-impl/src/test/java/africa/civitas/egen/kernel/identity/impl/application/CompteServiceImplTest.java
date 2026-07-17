package africa.civitas.egen.kernel.identity.impl.application;

import africa.civitas.egen.kernel.identity.api.command.CreerCompteCommand;
import africa.civitas.egen.kernel.identity.api.command.CreerPersonneCommand;
import africa.civitas.egen.kernel.identity.api.domain.Compte;
import africa.civitas.egen.kernel.identity.api.domain.MethodeAuthentification;
import africa.civitas.egen.kernel.identity.api.domain.Personne;
import africa.civitas.egen.kernel.identity.api.domain.StatutVital;
import africa.civitas.egen.kernel.identity.api.domain.TypeCompte;
import africa.civitas.egen.kernel.identity.api.exception.IdentiteConflitException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CompteServiceImplTest {

    @Inject
    PersonneServiceImpl personneService;

    @Inject
    CompteServiceImpl compteService;

    private Personne unePersonne(String ref) {
        return personneService.creer(new CreerPersonneCommand(
                ref, "Mba", null, List.of("Samuel"), LocalDate.of(1990, 1, 1),
                null, null, List.of("GA"), List.of(), StatutVital.VIVANT,
                null, null, null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void creerPersistsAndReturnsTheNewCompte() {
        Personne p = unePersonne("GA-CPT-001");

        Compte c = compteService.creer(new CreerCompteCommand(
                "kc-1", "samuel.mba", TypeCompte.STANDARD, MethodeAuthentification.MFA,
                p.id(), null, Acteur.systeme("test"), OrigineDonnee.SYNCHRONISEE));

        assertEquals(p.id(), c.personneId());
        assertTrue(c.estUtilisable());
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownPersonne() {
        assertThrows(PersonneIntrouvableException.class, () -> compteService.creer(new CreerCompteCommand(
                "kc-2", "inconnu", TypeCompte.STANDARD, MethodeAuthentification.MFA,
                UUID.randomUUID(), null, Acteur.systeme("test"), OrigineDonnee.SYNCHRONISEE)));
    }

    @Test
    @TestTransaction
    void creerRejectsADuplicateKeycloakId() {
        Personne p = unePersonne("GA-CPT-002");
        compteService.creer(new CreerCompteCommand(
                "kc-dup", "user1", TypeCompte.STANDARD, MethodeAuthentification.MFA,
                p.id(), null, Acteur.systeme("test"), OrigineDonnee.SYNCHRONISEE));

        assertThrows(IdentiteConflitException.class, () -> compteService.creer(new CreerCompteCommand(
                "kc-dup", "user2", TypeCompte.STANDARD, MethodeAuthentification.MFA,
                p.id(), null, Acteur.systeme("test"), OrigineDonnee.SYNCHRONISEE)));
    }

    @Test
    @TestTransaction
    void listerParPersonneReturnsAllAccountsOfThatPersonne() {
        Personne p = unePersonne("GA-CPT-003");
        compteService.creer(new CreerCompteCommand(
                "kc-3a", "user3a", TypeCompte.STANDARD, MethodeAuthentification.MFA,
                p.id(), null, Acteur.systeme("test"), OrigineDonnee.SYNCHRONISEE));
        compteService.creer(new CreerCompteCommand(
                "kc-3b", "user3b", TypeCompte.COMPTE_DE_SERVICE, MethodeAuthentification.MOT_DE_PASSE_SEUL,
                p.id(), null, Acteur.systeme("test"), OrigineDonnee.SYNCHRONISEE));

        assertEquals(2, compteService.listerParPersonne(p.id()).size());
    }
}
