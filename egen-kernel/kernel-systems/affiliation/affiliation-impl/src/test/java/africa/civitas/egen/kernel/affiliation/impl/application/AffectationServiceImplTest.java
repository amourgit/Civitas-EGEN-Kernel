package africa.civitas.egen.kernel.affiliation.impl.application;

import africa.civitas.egen.kernel.affiliation.api.command.CreerAffectationCommand;
import africa.civitas.egen.kernel.affiliation.api.command.CreerLexiqueDesMandatsCommand;
import africa.civitas.egen.kernel.affiliation.api.command.CreerMandatCommand;
import africa.civitas.egen.kernel.affiliation.api.command.TerminerAffectationCommand;
import africa.civitas.egen.kernel.affiliation.api.domain.Affectation;
import africa.civitas.egen.kernel.affiliation.api.domain.LexiqueDesMandats;
import africa.civitas.egen.kernel.affiliation.api.domain.Mandat;
import africa.civitas.egen.kernel.affiliation.api.domain.MotifFinAffectation;
import africa.civitas.egen.kernel.affiliation.api.domain.QuotiteEngagement;
import africa.civitas.egen.kernel.affiliation.api.domain.StatutAffectation;
import africa.civitas.egen.kernel.identity.api.command.CreerPersonneCommand;
import africa.civitas.egen.kernel.identity.api.domain.Personne;
import africa.civitas.egen.kernel.identity.api.domain.StatutVital;
import africa.civitas.egen.kernel.identity.api.exception.PersonneIntrouvableException;
import africa.civitas.egen.kernel.identity.api.service.PersonneService;
import africa.civitas.egen.kernel.organization.api.command.CreerCelluleCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerLexiqueOrganisationnelCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerTypeCelluleCommand;
import africa.civitas.egen.kernel.organization.api.domain.Cellule;
import africa.civitas.egen.kernel.organization.api.domain.LexiqueOrganisationnel;
import africa.civitas.egen.kernel.organization.api.domain.Organisation;
import africa.civitas.egen.kernel.organization.api.domain.TypeCellule;
import africa.civitas.egen.kernel.organization.api.domain.TypeOrganisation;
import africa.civitas.egen.kernel.organization.api.exception.CelluleIntrouvableException;
import africa.civitas.egen.kernel.organization.api.service.CelluleService;
import africa.civitas.egen.kernel.organization.api.service.LexiqueOrganisationnelService;
import africa.civitas.egen.kernel.organization.api.service.OrganisationService;
import africa.civitas.egen.kernel.organization.api.service.TypeCelluleService;
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
class AffectationServiceImplTest {

    @Inject
    PersonneService personneService;

    @Inject
    OrganisationService organisationService;

    @Inject
    LexiqueOrganisationnelService lexiqueOrganisationnelService;

    @Inject
    TypeCelluleService typeCelluleService;

    @Inject
    CelluleService celluleService;

    @Inject
    LexiqueDesMandatsServiceImpl lexiqueDesMandatsService;

    @Inject
    MandatServiceImpl mandatService;

    @Inject
    AffectationServiceImpl service;

    private Personne unePersonne(String ref) {
        return personneService.creer(new CreerPersonneCommand(
                ref, "Mba", null, List.of("Samuel"), LocalDate.of(1990, 1, 1),
                "Libreville", null, List.of("GA"), List.of("fr"), StatutVital.VIVANT,
                null, null, null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    private Cellule uneCellule(String identifiantJuridique, String codeInterne) {
        Organisation o = organisationService.creer(new CreerOrganisationCommand(
                "Universite Numerique", "UN", TypeOrganisation.UNIVERSITE, "Education superieure",
                "GA", identifiantJuridique, "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        LexiqueOrganisationnel l = lexiqueOrganisationnelService.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique", "Description", null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        TypeCellule type = typeCelluleService.creer(new CreerTypeCelluleCommand(
                l.id(), "Departement", "Description", 0, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        return celluleService.creer(new CreerCelluleCommand(
                o.id(), null, type.id(), "Departement Reseau", codeInterne, "Description",
                null, null, LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    private Mandat unMandat(String identifiantJuridique) {
        Organisation o = organisationService.creer(new CreerOrganisationCommand(
                "Ministere", "MIN", TypeOrganisation.MINISTERE, "Education",
                "GA", identifiantJuridique, "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        LexiqueDesMandats lm = lexiqueDesMandatsService.creer(new CreerLexiqueDesMandatsCommand(
                o.id(), "Lexique des Mandats", "Description", null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        return mandatService.creer(new CreerMandatCommand(
                lm.id(), "Enseignant", "Dispense les cours", 1, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void creerVerifiesBothPersonneAndCelluleThroughTheRealBridge() {
        Personne p = unePersonne("GA-AFF-001");
        Cellule c = uneCellule("RCCM-AFF-001", "DEP-001");
        Mandat m = unMandat("RCCM-AFF-002");

        Affectation a = service.creer(new CreerAffectationCommand(
                p.id(), c.id(), m.id(), QuotiteEngagement.TEMPS_PLEIN, LocalDate.of(2025, 9, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(StatutAffectation.ACTIVE, a.statut());
        assertEquals(1L, a.tracabilite().version());
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownPersonne() {
        Cellule c = uneCellule("RCCM-AFF-003", "DEP-002");
        Mandat m = unMandat("RCCM-AFF-004");

        assertThrows(PersonneIntrouvableException.class, () -> service.creer(new CreerAffectationCommand(
                UUID.randomUUID(), c.id(), m.id(), QuotiteEngagement.TEMPS_PLEIN, LocalDate.of(2025, 9, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownCellule() {
        Personne p = unePersonne("GA-AFF-005");
        Mandat m = unMandat("RCCM-AFF-006");

        assertThrows(CelluleIntrouvableException.class, () -> service.creer(new CreerAffectationCommand(
                p.id(), UUID.randomUUID(), m.id(), QuotiteEngagement.TEMPS_PLEIN, LocalDate.of(2025, 9, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void terminerIncrementsVersionAndRequiresAMotif() {
        Personne p = unePersonne("GA-AFF-007");
        Cellule c = uneCellule("RCCM-AFF-007", "DEP-003");
        Mandat m = unMandat("RCCM-AFF-008");
        Affectation a = service.creer(new CreerAffectationCommand(
                p.id(), c.id(), m.id(), QuotiteEngagement.TEMPS_PLEIN, LocalDate.of(2025, 9, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        Affectation terminee = service.terminer(new TerminerAffectationCommand(
                a.id(), LocalDate.of(2026, 6, 30), MotifFinAffectation.FIN_DE_CONTRAT,
                Acteur.systeme("test"), "Fin de l'annee academique"));

        assertEquals(StatutAffectation.TERMINEE, terminee.statut());
        assertEquals(2L, terminee.tracabilite().version());
    }

    @Test
    @TestTransaction
    void listerParPersonneAndListerParCelluleFindTheAffectation() {
        Personne p = unePersonne("GA-AFF-009");
        Cellule c = uneCellule("RCCM-AFF-009", "DEP-004");
        Mandat m = unMandat("RCCM-AFF-010");
        service.creer(new CreerAffectationCommand(
                p.id(), c.id(), m.id(), QuotiteEngagement.MI_TEMPS, LocalDate.of(2025, 9, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(1, service.listerParPersonne(p.id()).size());
        assertEquals(1, service.listerParCellule(c.id()).size());
    }
}
