package africa.civitas.egen.modules.business.organization.impl.politique.application;

import africa.civitas.egen.modules.business.organization.api.command.CreerCelluleCommand;
import africa.civitas.egen.modules.business.organization.api.command.CreerLexiqueOrganisationnelCommand;
import africa.civitas.egen.modules.business.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.modules.business.organization.api.command.CreerTypeCelluleCommand;
import africa.civitas.egen.modules.business.organization.api.domain.Cellule;
import africa.civitas.egen.modules.business.organization.api.domain.LexiqueOrganisationnel;
import africa.civitas.egen.modules.business.organization.api.domain.Organisation;
import africa.civitas.egen.modules.business.organization.api.domain.TypeCellule;
import africa.civitas.egen.modules.business.organization.api.domain.TypeOrganisation;
import africa.civitas.egen.modules.business.organization.api.exception.CelluleIntrouvableException;
import africa.civitas.egen.modules.business.organization.api.service.CelluleService;
import africa.civitas.egen.modules.business.organization.api.service.LexiqueOrganisationnelService;
import africa.civitas.egen.modules.business.organization.api.service.OrganisationService;
import africa.civitas.egen.modules.business.organization.api.service.TypeCelluleService;
import africa.civitas.egen.modules.business.organization.api.politique.command.CreerDerogationCommand;
import africa.civitas.egen.modules.business.organization.api.politique.command.CreerPolitiqueCommand;
import africa.civitas.egen.modules.business.organization.api.politique.domain.DomainePolitique;
import africa.civitas.egen.modules.business.organization.api.politique.domain.Politique;
import africa.civitas.egen.modules.business.organization.api.politique.exception.PolitiqueIntrouvableException;
import africa.civitas.egen.kernel.sdk.contexte.ContexteNature;
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
class DerogationServiceImplTest {

    @Inject
    OrganisationService organisationService;

    @Inject
    LexiqueOrganisationnelService lexiqueOrganisationnelService;

    @Inject
    TypeCelluleService typeCelluleService;

    @Inject
    CelluleService celluleService;

    @Inject
    PolitiqueServiceImpl politiqueService;

    @Inject
    DerogationServiceImpl service;

    private Cellule uneCellule(String identifiantJuridique) {
        Organisation o = organisationService.creer(new CreerOrganisationCommand(
                "Universite Numerique", "UN", TypeOrganisation.UNIVERSITE, "Education superieure",
                "GA", identifiantJuridique, "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        LexiqueOrganisationnel l = lexiqueOrganisationnelService.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique", "Description", null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        TypeCellule type = typeCelluleService.creer(new CreerTypeCelluleCommand(
                l.id(), "Etablissement", "Description", 0, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        return celluleService.creer(new CreerCelluleCommand(
                o.id(), null, type.id(), "Universite", "UN-" + identifiantJuridique, "Description",
                null, null, LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    private Politique unePolitique(UUID organisationId) {
        return politiqueService.creer(new CreerPolitiqueCommand(
                organisationId, ContexteNature.ORGANISATION, DomainePolitique.SECURITE_MOT_DE_PASSE,
                "Longueur minimale", "12 caracteres", LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void creerPersistsAndReturnsTheDerogation() {
        Cellule c = uneCellule("RCCM-DER-001");
        Politique p = unePolitique(c.organisationId());

        var d = service.creer(new CreerDerogationCommand(
                p.id(), c.id(), "8 caracteres", "Justification",
                LocalDate.of(2025, 1, 1), null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(p.id(), d.politiqueId());
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownPolitique() {
        Cellule c = uneCellule("RCCM-DER-002");

        assertThrows(PolitiqueIntrouvableException.class, () -> service.creer(new CreerDerogationCommand(
                UUID.randomUUID(), c.id(), "8 caracteres", "Justification",
                LocalDate.of(2025, 1, 1), null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownCellule() {
        Cellule c = uneCellule("RCCM-DER-003");
        Politique p = unePolitique(c.organisationId());

        assertThrows(CelluleIntrouvableException.class, () -> service.creer(new CreerDerogationCommand(
                p.id(), UUID.randomUUID(), "8 caracteres", "Justification",
                LocalDate.of(2025, 1, 1), null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void listerParPolitiqueFindsTheDerogation() {
        Cellule c = uneCellule("RCCM-DER-004");
        Politique p = unePolitique(c.organisationId());
        service.creer(new CreerDerogationCommand(
                p.id(), c.id(), "8 caracteres", "Justification",
                LocalDate.of(2025, 1, 1), null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(1, service.listerParPolitique(p.id()).size());
    }
}
