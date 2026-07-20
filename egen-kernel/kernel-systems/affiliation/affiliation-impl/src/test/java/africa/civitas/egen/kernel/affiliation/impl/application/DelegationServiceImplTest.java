package africa.civitas.egen.kernel.affiliation.impl.application;

import africa.civitas.egen.kernel.affiliation.api.command.CreerAffectationCommand;
import africa.civitas.egen.kernel.affiliation.api.command.CreerDelegationCommand;
import africa.civitas.egen.kernel.affiliation.api.command.CreerLexiqueDesMandatsCommand;
import africa.civitas.egen.kernel.affiliation.api.command.CreerMandatCommand;
import africa.civitas.egen.kernel.affiliation.api.domain.Affectation;
import africa.civitas.egen.kernel.affiliation.api.domain.Delegation;
import africa.civitas.egen.kernel.affiliation.api.domain.EtendueDelegation;
import africa.civitas.egen.kernel.affiliation.api.domain.LexiqueDesMandats;
import africa.civitas.egen.kernel.affiliation.api.domain.Mandat;
import africa.civitas.egen.kernel.affiliation.api.domain.MotifDelegation;
import africa.civitas.egen.kernel.affiliation.api.domain.QuotiteEngagement;
import africa.civitas.egen.kernel.affiliation.api.domain.StatutDelegation;
import africa.civitas.egen.kernel.affiliation.api.exception.AffectationIntrouvableException;
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
class DelegationServiceImplTest {

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
    AffectationServiceImpl affectationService;

    @Inject
    DelegationServiceImpl service;

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
                lm.id(), "Directeur", "Dirige", 5, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    private Affectation uneAffectation(String refPersonne, String refCellule, String refMandat) {
        Personne p = unePersonne(refPersonne);
        Cellule c = uneCellule(refCellule, "DEP-" + refCellule);
        Mandat m = unMandat(refMandat);
        return affectationService.creer(new CreerAffectationCommand(
                p.id(), c.id(), m.id(), QuotiteEngagement.TEMPS_PLEIN, LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void creerAProgrammeeDelegationSucceeds() {
        Affectation origine = uneAffectation("GA-DEL-001", "RCCM-DEL-001", "RCCM-DEL-002");
        Personne beneficiaire = unePersonne("GA-DEL-002");

        Delegation d = service.creer(new CreerDelegationCommand(
                origine.id(), beneficiaire.id(), EtendueDelegation.TOTALE, null,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20), MotifDelegation.CONGE,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(StatutDelegation.PROGRAMMEE, d.statut());
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownAffectationOrigine() {
        Personne beneficiaire = unePersonne("GA-DEL-003");

        assertThrows(AffectationIntrouvableException.class, () -> service.creer(new CreerDelegationCommand(
                UUID.randomUUID(), beneficiaire.id(), EtendueDelegation.TOTALE, null,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20), MotifDelegation.VOYAGE,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownPersonneBeneficiaire() {
        Affectation origine = uneAffectation("GA-DEL-004", "RCCM-DEL-003", "RCCM-DEL-004");

        assertThrows(PersonneIntrouvableException.class, () -> service.creer(new CreerDelegationCommand(
                origine.id(), UUID.randomUUID(), EtendueDelegation.TOTALE, null,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20), MotifDelegation.ABSENCE,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void listerParAffectationOrigineFindsTheDelegation() {
        Affectation origine = uneAffectation("GA-DEL-005", "RCCM-DEL-005", "RCCM-DEL-006");
        Personne beneficiaire = unePersonne("GA-DEL-006");
        service.creer(new CreerDelegationCommand(
                origine.id(), beneficiaire.id(), EtendueDelegation.PARTIELLE, "Signature des bulletins",
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(20), MotifDelegation.VACANCE_DE_POSTE,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(1, service.listerParAffectationOrigine(origine.id()).size());
        assertEquals(1, service.listerParPersonneBeneficiaire(beneficiaire.id()).size());
    }
}
