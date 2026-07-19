package africa.civitas.egen.kernel.organization.impl.application;

import africa.civitas.egen.kernel.organization.api.command.CreerCelluleCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerLexiqueOrganisationnelCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerSuccessionOrganisationnelleCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerTypeCelluleCommand;
import africa.civitas.egen.kernel.organization.api.domain.Cellule;
import africa.civitas.egen.kernel.organization.api.domain.LexiqueOrganisationnel;
import africa.civitas.egen.kernel.organization.api.domain.NatureSuccession;
import africa.civitas.egen.kernel.organization.api.domain.Organisation;
import africa.civitas.egen.kernel.organization.api.domain.SuccessionOrganisationnelle;
import africa.civitas.egen.kernel.organization.api.domain.TypeCellule;
import africa.civitas.egen.kernel.organization.api.domain.TypeOrganisation;
import africa.civitas.egen.kernel.organization.api.exception.CelluleIntrouvableException;
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
class SuccessionOrganisationnelleServiceImplTest {

    @Inject
    OrganisationServiceImpl organisationService;

    @Inject
    LexiqueOrganisationnelServiceImpl lexiqueService;

    @Inject
    TypeCelluleServiceImpl typeCelluleService;

    @Inject
    CelluleServiceImpl celluleService;

    @Inject
    SuccessionOrganisationnelleServiceImpl service;

    private Cellule uneCellule(String identifiantJuridique, String codeInterne) {
        Organisation o = organisationService.creer(new CreerOrganisationCommand(
                "Ministere de l'Education", "MINEDUC", TypeOrganisation.MINISTERE, "Education",
                "GA", identifiantJuridique, "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        LexiqueOrganisationnel l = lexiqueService.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique", "Description", null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        TypeCellule type = typeCelluleService.creer(new CreerTypeCelluleCommand(
                l.id(), "Departement", "Description", 0, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        return celluleService.creer(new CreerCelluleCommand(
                o.id(), null, type.id(), "Departement", codeInterne, "Description",
                null, null, LocalDate.of(2020, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void creerAFusionPersistsAndReturnsIt() {
        Cellule origine1 = uneCellule("RCCM-SUC-001", "DEP-INFO");
        Cellule origine2 = uneCellule("RCCM-SUC-002", "DEP-RES");
        Cellule heritiere = uneCellule("RCCM-SUC-003", "DEP-NUM");

        SuccessionOrganisationnelle s = service.creer(new CreerSuccessionOrganisationnelleCommand(
                List.of(origine1.id(), origine2.id()), List.of(heritiere.id()), NatureSuccession.FUSION,
                LocalDate.of(2027, 1, 1), "Reorganisation ministerielle 2027",
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(2, s.celluleOrigineIds().size());
        assertEquals(NatureSuccession.FUSION, s.nature());
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownCelluleOrigine() {
        Cellule heritiere = uneCellule("RCCM-SUC-004", "DEP-NUM");

        assertThrows(CelluleIntrouvableException.class, () -> service.creer(
                new CreerSuccessionOrganisationnelleCommand(
                        List.of(UUID.randomUUID()), List.of(heritiere.id()), NatureSuccession.RENOMMAGE,
                        LocalDate.of(2027, 1, 1), null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void listerParCelluleFindsBothOriginAndHeirRoles() {
        Cellule origine = uneCellule("RCCM-SUC-005", "DEP-A");
        Cellule heritiere = uneCellule("RCCM-SUC-006", "DEP-B");
        service.creer(new CreerSuccessionOrganisationnelleCommand(
                List.of(origine.id()), List.of(heritiere.id()), NatureSuccession.RENOMMAGE,
                LocalDate.of(2027, 1, 1), null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(1, service.listerParCellule(origine.id()).size());
        assertEquals(1, service.listerParCellule(heritiere.id()).size());
    }
}
