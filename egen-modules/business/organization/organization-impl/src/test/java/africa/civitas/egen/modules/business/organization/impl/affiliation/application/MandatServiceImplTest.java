package africa.civitas.egen.modules.business.organization.impl.affiliation.application;

import africa.civitas.egen.modules.business.organization.api.affiliation.command.CreerLexiqueDesMandatsCommand;
import africa.civitas.egen.modules.business.organization.api.affiliation.command.CreerMandatCommand;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.LexiqueDesMandats;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.Mandat;
import africa.civitas.egen.modules.business.organization.api.affiliation.exception.LexiqueDesMandatsIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.modules.business.organization.api.domain.Organisation;
import africa.civitas.egen.modules.business.organization.api.domain.TypeOrganisation;
import africa.civitas.egen.modules.business.organization.api.service.OrganisationService;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class MandatServiceImplTest {

    @Inject
    OrganisationService organisationService;

    @Inject
    LexiqueDesMandatsServiceImpl lexiqueService;

    @Inject
    MandatServiceImpl service;

    private LexiqueDesMandats unLexique(String identifiantJuridique) {
        Organisation o = organisationService.creer(new CreerOrganisationCommand(
                "Ministere de l'Education", "MINEDUC", TypeOrganisation.MINISTERE, "Education",
                "GA", identifiantJuridique, "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        return lexiqueService.creer(new CreerLexiqueDesMandatsCommand(
                o.id(), "Lexique des Mandats", "Description", null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void creerAMandatWithoutSupervisionSucceeds() {
        LexiqueDesMandats l = unLexique("RCCM-AFF-MAN-001");

        Mandat m = service.creer(new CreerMandatCommand(
                l.id(), "Enseignant", "Dispense les cours", 1, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertTrue(m.mandatsSupervisesIds().isEmpty());
    }

    @Test
    @TestTransaction
    void creerAMandatSupervisingAnotherWithinTheSameLexiqueSucceeds() {
        LexiqueDesMandats l = unLexique("RCCM-AFF-MAN-002");
        Mandat enseignant = service.creer(new CreerMandatCommand(
                l.id(), "Enseignant", "Dispense les cours", 1, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        Mandat chefDepartement = service.creer(new CreerMandatCommand(
                l.id(), "Chef de Departement", "Supervise", 5, List.of(enseignant.id()), null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(List.of(enseignant.id()), chefDepartement.mandatsSupervisesIds());
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownLexique() {
        assertThrows(LexiqueDesMandatsIntrouvableException.class, () -> service.creer(
                new CreerMandatCommand(
                        UUID.randomUUID(), "Enseignant", "Description", 1, null, null,
                        Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }
}
