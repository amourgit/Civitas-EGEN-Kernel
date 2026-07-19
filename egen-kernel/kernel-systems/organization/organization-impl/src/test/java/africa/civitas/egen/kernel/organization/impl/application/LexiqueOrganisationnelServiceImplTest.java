package africa.civitas.egen.kernel.organization.impl.application;

import africa.civitas.egen.kernel.organization.api.command.CreerLexiqueOrganisationnelCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.kernel.organization.api.domain.LexiqueOrganisationnel;
import africa.civitas.egen.kernel.organization.api.domain.Organisation;
import africa.civitas.egen.kernel.organization.api.domain.TypeOrganisation;
import africa.civitas.egen.kernel.organization.api.exception.OrganisationIntrouvableException;
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

@QuarkusTest
class LexiqueOrganisationnelServiceImplTest {

    @Inject
    OrganisationServiceImpl organisationService;

    @Inject
    LexiqueOrganisationnelServiceImpl service;

    private Organisation uneOrganisation(String identifiantJuridique) {
        return organisationService.creer(new CreerOrganisationCommand(
                "Ministere de l'Education", "MINEDUC", TypeOrganisation.MINISTERE, "Education",
                "GA", identifiantJuridique, "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void creerPersistsAndReturnsTheLexique() {
        Organisation o = uneOrganisation("RCCM-LEX-001");

        LexiqueOrganisationnel l = service.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique Academique", "Vocabulaire academique", null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(o.id(), l.organisationId());
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownOrganisation() {
        assertThrows(OrganisationIntrouvableException.class, () -> service.creer(
                new CreerLexiqueOrganisationnelCommand(
                        UUID.randomUUID(), "Lexique Academique", "Description", null,
                        Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }
}
