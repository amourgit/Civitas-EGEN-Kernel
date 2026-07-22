package africa.civitas.egen.modules.business.organization.impl.application;

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
import africa.civitas.egen.modules.business.organization.api.exception.OrganisationnelConflitException;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CelluleServiceImplTest {

    @Inject
    OrganisationServiceImpl organisationService;

    @Inject
    LexiqueOrganisationnelServiceImpl lexiqueService;

    @Inject
    TypeCelluleServiceImpl typeCelluleService;

    @Inject
    CelluleServiceImpl service;

    private Organisation uneOrganisation(String identifiantJuridique) {
        return organisationService.creer(new CreerOrganisationCommand(
                "Universite Numerique", "UN", TypeOrganisation.UNIVERSITE, "Education superieure",
                "GA", identifiantJuridique, "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    private TypeCellule unType(UUID lexiqueId, String libelle, List<UUID> parentsAutorises) {
        return typeCelluleService.creer(new CreerTypeCelluleCommand(
                lexiqueId, libelle, "Description", 0, parentsAutorises, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void creerUnEtablissementSucceeds() {
        Organisation o = uneOrganisation("RCCM-CEL-001");
        LexiqueOrganisationnel l = lexiqueService.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique", "Description", null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        TypeCellule typeEtablissement = unType(l.id(), "Etablissement", null);

        Cellule etablissement = service.creer(new CreerCelluleCommand(
                o.id(), null, typeEtablissement.id(), "Universite Numerique", "UN-001",
                "Description", "GA", null, LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertTrue(etablissement.estEtablissement());
    }

    @Test
    @TestTransaction
    void rejectsADuplicateCodeInterneWithinTheSameOrganisation() {
        Organisation o = uneOrganisation("RCCM-CEL-002");
        LexiqueOrganisationnel l = lexiqueService.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique", "Description", null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        TypeCellule type = unType(l.id(), "Etablissement", null);

        service.creer(new CreerCelluleCommand(
                o.id(), null, type.id(), "Universite Numerique", "UN-001",
                "Description", null, null, LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertThrows(OrganisationnelConflitException.class, () -> service.creer(new CreerCelluleCommand(
                o.id(), null, type.id(), "Universite Bis", "UN-001",
                "Description", null, null, LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownCelluleParent() {
        Organisation o = uneOrganisation("RCCM-CEL-003");
        LexiqueOrganisationnel l = lexiqueService.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique", "Description", null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        TypeCellule type = unType(l.id(), "Faculte", null);

        assertThrows(CelluleIntrouvableException.class, () -> service.creer(new CreerCelluleCommand(
                o.id(), UUID.randomUUID(), type.id(), "Faculte Informatique", "FAC-001",
                "Description", null, null, LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void creerRejectsAParentTypeNotInTheAllowedList() {
        Organisation o = uneOrganisation("RCCM-CEL-004");
        LexiqueOrganisationnel l = lexiqueService.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique", "Description", null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        TypeCellule typeEtablissement = unType(l.id(), "Etablissement", null);
        TypeCellule typeAutreType = unType(l.id(), "Autre", null);
        // Faculte n'autorise QUE "Autre" comme parent, pas Etablissement
        TypeCellule typeFaculte = unType(l.id(), "Faculte", List.of(typeAutreType.id()));

        Cellule etablissement = service.creer(new CreerCelluleCommand(
                o.id(), null, typeEtablissement.id(), "Universite Numerique", "UN-001",
                "Description", null, null, LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertThrows(IllegalArgumentException.class, () -> service.creer(new CreerCelluleCommand(
                o.id(), etablissement.id(), typeFaculte.id(), "Faculte Informatique", "FAC-001",
                "Description", null, null, LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    /**
     * Le test central de tout le systeme A2 : construit Universite > Faculte >
     * Departement > Classe (4 niveaux), et verifie que listerDescendants retourne
     * exactement les bons ensembles a chaque niveau, via la Fermeture Transitive —
     * jamais de recursion applicative.
     */
    @Test
    @TestTransaction
    void fermetureTransitiveReflectsTheFullDepthOfTheTree() {
        Organisation o = uneOrganisation("RCCM-CEL-005");
        LexiqueOrganisationnel l = lexiqueService.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique", "Description", null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        TypeCellule type = unType(l.id(), "Generique", null);

        Cellule universite = service.creer(new CreerCelluleCommand(
                o.id(), null, type.id(), "Universite Numerique", "UN", "Description",
                null, null, LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        Cellule faculte = service.creer(new CreerCelluleCommand(
                o.id(), universite.id(), type.id(), "Faculte Informatique", "FAC", "Description",
                null, null, LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        Cellule departement = service.creer(new CreerCelluleCommand(
                o.id(), faculte.id(), type.id(), "Departement Reseau", "DEP", "Description",
                null, null, LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        Cellule classe = service.creer(new CreerCelluleCommand(
                o.id(), departement.id(), type.id(), "Classe L3", "CLS", "Description",
                null, null, LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        // Depuis l'Universite : les 3 descendants, tous niveaux confondus
        Set<UUID> descendantsUniversite = service.listerDescendants(universite.id()).stream()
                .map(Cellule::id).collect(Collectors.toSet());
        assertEquals(Set.of(faculte.id(), departement.id(), classe.id()), descendantsUniversite);

        // Depuis la Faculte : seulement Departement et Classe, jamais l'Universite (son ancetre)
        Set<UUID> descendantsFaculte = service.listerDescendants(faculte.id()).stream()
                .map(Cellule::id).collect(Collectors.toSet());
        assertEquals(Set.of(departement.id(), classe.id()), descendantsFaculte);

        // Depuis la Classe (feuille) : aucun descendant
        assertTrue(service.listerDescendants(classe.id()).isEmpty());

        // listerEtablissements ne doit remonter que l'Universite (la seule Cellule racine)
        List<Cellule> etablissements = service.listerEtablissements(o.id());
        assertEquals(1, etablissements.size());
        assertEquals(universite.id(), etablissements.get(0).id());

        // listerParOrganisation doit remonter les 4 Cellules
        assertEquals(4, service.listerParOrganisation(o.id()).size());

        // listerAncetres, depuis la Classe : Departement, Faculte, Universite, dans cet ordre exact
        // (du plus proche au plus eloigne) — la regle de resolution des Derogations (Politique organisationnelle, §B.12) en depend.
        List<UUID> ancetresDeLaClasse = service.listerAncetres(classe.id()).stream()
                .map(Cellule::id).toList();
        assertEquals(List.of(departement.id(), faculte.id(), universite.id()), ancetresDeLaClasse);

        // listerAncetres, depuis l'Universite (un Etablissement) : aucun ancetre
        assertTrue(service.listerAncetres(universite.id()).isEmpty());
    }
}
