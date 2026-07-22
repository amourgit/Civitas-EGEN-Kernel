package africa.civitas.egen.modules.business.organization.impl.affiliation.application;

import africa.civitas.egen.modules.business.organization.api.affiliation.command.CreerLexiqueDesMandatsCommand;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.LexiqueDesMandats;
import africa.civitas.egen.modules.business.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.modules.business.organization.api.domain.Organisation;
import africa.civitas.egen.modules.business.organization.api.domain.TypeOrganisation;
import africa.civitas.egen.modules.business.organization.api.exception.OrganisationIntrouvableException;
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

@QuarkusTest
class LexiqueDesMandatsServiceImplTest {

    @Inject
    OrganisationService organisationService;

    @Inject
    LexiqueDesMandatsServiceImpl service;

    private Organisation uneOrganisation(String identifiantJuridique) {
        return organisationService.creer(new CreerOrganisationCommand(
                "Ministere de l'Education", "MINEDUC", TypeOrganisation.MINISTERE, "Education",
                "GA", identifiantJuridique, "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void creerPersistsAndReturnsTheLexique() {
        Organisation o = uneOrganisation("RCCM-AFF-LEX-001");

        LexiqueDesMandats l = service.creer(new CreerLexiqueDesMandatsCommand(
                o.id(), "Lexique des Mandats", "Description", null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(o.id(), l.organisationId());
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownOrganisation() {
        assertThrows(OrganisationIntrouvableException.class, () -> service.creer(
                new CreerLexiqueDesMandatsCommand(
                        UUID.randomUUID(), "Lexique", "Description", null,
                        Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }
}
