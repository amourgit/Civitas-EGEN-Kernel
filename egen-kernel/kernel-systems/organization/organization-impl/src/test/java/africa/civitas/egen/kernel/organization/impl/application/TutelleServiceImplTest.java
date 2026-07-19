package africa.civitas.egen.kernel.organization.impl.application;

import africa.civitas.egen.kernel.organization.api.command.CreerCelluleCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerLexiqueOrganisationnelCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerTutelleCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerTypeCelluleCommand;
import africa.civitas.egen.kernel.organization.api.domain.Cellule;
import africa.civitas.egen.kernel.organization.api.domain.LexiqueOrganisationnel;
import africa.civitas.egen.kernel.organization.api.domain.NatureTutelle;
import africa.civitas.egen.kernel.organization.api.domain.Organisation;
import africa.civitas.egen.kernel.organization.api.domain.TypeCellule;
import africa.civitas.egen.kernel.organization.api.domain.TypeOrganisation;
import africa.civitas.egen.kernel.organization.api.domain.Tutelle;
import africa.civitas.egen.kernel.organization.api.exception.OrganisationnelConflitException;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class TutelleServiceImplTest {

    @Inject
    OrganisationServiceImpl organisationService;

    @Inject
    LexiqueOrganisationnelServiceImpl lexiqueService;

    @Inject
    TypeCelluleServiceImpl typeCelluleService;

    @Inject
    CelluleServiceImpl celluleService;

    @Inject
    TutelleServiceImpl service;

    private Organisation uneOrganisation(String identifiantJuridique, String raisonSociale) {
        return organisationService.creer(new CreerOrganisationCommand(
                raisonSociale, "SIGLE", TypeOrganisation.MINISTERE, "Sante",
                "GA", identifiantJuridique, "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    private Cellule unEtablissement(Organisation o) {
        LexiqueOrganisationnel l = lexiqueService.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique", "Description", null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        TypeCellule type = typeCelluleService.creer(new CreerTypeCelluleCommand(
                l.id(), "Etablissement", "Description", 0, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        return celluleService.creer(new CreerCelluleCommand(
                o.id(), null, type.id(), "CHU de Libreville", "CHU-001", "Description",
                null, null, LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void unEtablissementPeutAvoirDeuxTutellesDeNatureDifferente() {
        Organisation ministereSante = uneOrganisation("RCCM-TUT-001", "Ministere de la Sante");
        Organisation universite = uneOrganisation("RCCM-TUT-002", "Universite des Sciences de la Sante");
        Cellule chu = unEtablissement(ministereSante);

        Tutelle principale = service.creer(new CreerTutelleCommand(
                chu.id(), ministereSante.id(), NatureTutelle.SANITAIRE, true,
                LocalDate.of(2025, 1, 1), null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        Tutelle academique = service.creer(new CreerTutelleCommand(
                chu.id(), universite.id(), NatureTutelle.ACADEMIQUE, false,
                LocalDate.of(2025, 1, 1), null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(2, service.listerParCellule(chu.id()).size());
        assertEquals(true, principale.tutellePrincipale());
        assertEquals(false, academique.tutellePrincipale());
    }

    @Test
    @TestTransaction
    void rejectsASecondTutellePrincipalePourLeMemeEtablissement() {
        Organisation ministereSante = uneOrganisation("RCCM-TUT-003", "Ministere de la Sante");
        Organisation universite = uneOrganisation("RCCM-TUT-004", "Universite des Sciences de la Sante");
        Cellule chu = unEtablissement(ministereSante);

        service.creer(new CreerTutelleCommand(
                chu.id(), ministereSante.id(), NatureTutelle.SANITAIRE, true,
                LocalDate.of(2025, 1, 1), null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertThrows(OrganisationnelConflitException.class, () -> service.creer(new CreerTutelleCommand(
                chu.id(), universite.id(), NatureTutelle.ACADEMIQUE, true,
                LocalDate.of(2025, 1, 1), null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void rejectsATutelleOnANonRootCellule() {
        Organisation o = uneOrganisation("RCCM-TUT-005", "Universite Numerique");
        LexiqueOrganisationnel l = lexiqueService.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique", "Description", null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        TypeCellule type = typeCelluleService.creer(new CreerTypeCelluleCommand(
                l.id(), "Generique", "Description", 0, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        Cellule etablissement = celluleService.creer(new CreerCelluleCommand(
                o.id(), null, type.id(), "Universite", "UN", "Description",
                null, null, LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        Cellule faculte = celluleService.creer(new CreerCelluleCommand(
                o.id(), etablissement.id(), type.id(), "Faculte", "FAC", "Description",
                null, null, LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertThrows(IllegalArgumentException.class, () -> service.creer(new CreerTutelleCommand(
                faculte.id(), o.id(), NatureTutelle.ACADEMIQUE, false,
                LocalDate.of(2025, 1, 1), null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }
}
