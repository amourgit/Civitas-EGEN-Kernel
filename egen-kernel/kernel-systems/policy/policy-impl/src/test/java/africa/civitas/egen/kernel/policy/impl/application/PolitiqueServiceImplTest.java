package africa.civitas.egen.kernel.policy.impl.application;

import africa.civitas.egen.kernel.organization.api.command.CreerCelluleCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerLexiqueOrganisationnelCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.kernel.organization.api.command.CreerTypeCelluleCommand;
import africa.civitas.egen.kernel.organization.api.domain.Cellule;
import africa.civitas.egen.kernel.organization.api.domain.LexiqueOrganisationnel;
import africa.civitas.egen.kernel.organization.api.domain.Organisation;
import africa.civitas.egen.kernel.organization.api.domain.TypeCellule;
import africa.civitas.egen.kernel.organization.api.domain.TypeOrganisation;
import africa.civitas.egen.kernel.organization.api.exception.OrganisationIntrouvableException;
import africa.civitas.egen.kernel.organization.api.service.CelluleService;
import africa.civitas.egen.kernel.organization.api.service.LexiqueOrganisationnelService;
import africa.civitas.egen.kernel.organization.api.service.OrganisationService;
import africa.civitas.egen.kernel.organization.api.service.TypeCelluleService;
import africa.civitas.egen.kernel.policy.api.command.CreerDerogationCommand;
import africa.civitas.egen.kernel.policy.api.command.CreerPolitiqueCommand;
import africa.civitas.egen.kernel.policy.api.domain.DomainePolitique;
import africa.civitas.egen.kernel.policy.api.domain.Politique;
import africa.civitas.egen.kernel.policy.api.domain.ValeurEffective;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PolitiqueServiceImplTest {

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
    DerogationServiceImpl derogationService;

    /** Construit Universite (Etablissement) > Faculte > Departement, retourne les 3 Cellules dans cet ordre. */
    private List<Cellule> uneHierarchieA3Niveaux(String identifiantJuridique) {
        Organisation o = organisationService.creer(new CreerOrganisationCommand(
                "Universite Numerique", "UN", TypeOrganisation.UNIVERSITE, "Education superieure",
                "GA", identifiantJuridique, "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        LexiqueOrganisationnel l = lexiqueOrganisationnelService.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique", "Description", null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        TypeCellule type = typeCelluleService.creer(new CreerTypeCelluleCommand(
                l.id(), "Generique", "Description", 0, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        Cellule universite = celluleService.creer(new CreerCelluleCommand(
                o.id(), null, type.id(), "Universite", "UN-" + identifiantJuridique, "Description",
                null, null, LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        Cellule faculte = celluleService.creer(new CreerCelluleCommand(
                o.id(), universite.id(), type.id(), "Faculte", "FAC-" + identifiantJuridique, "Description",
                null, null, LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        Cellule departement = celluleService.creer(new CreerCelluleCommand(
                o.id(), faculte.id(), type.id(), "Departement", "DEP-" + identifiantJuridique, "Description",
                null, null, LocalDate.of(2025, 1, 1), Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        return List.of(universite, faculte, departement);
    }

    @Test
    @TestTransaction
    void creerOnOrganisationSucceeds() {
        List<Cellule> hierarchie = uneHierarchieA3Niveaux("RCCM-POL-001");
        Cellule universite = hierarchie.get(0);

        Politique p = politiqueService.creer(new CreerPolitiqueCommand(
                universite.organisationId(), ContexteNature.ORGANISATION, DomainePolitique.SECURITE_MOT_DE_PASSE,
                "Longueur minimale", "12 caracteres", LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals("12 caracteres", p.valeur());
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownOrganisationContexte() {
        assertThrows(OrganisationIntrouvableException.class, () -> politiqueService.creer(
                new CreerPolitiqueCommand(
                        UUID.randomUUID(), ContexteNature.ORGANISATION, DomainePolitique.SECURITE_MOT_DE_PASSE,
                        "Longueur minimale", "12 caracteres", LocalDate.of(2025, 1, 1),
                        Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }

    @Test
    @TestTransaction
    void resoudrePourCelluleReturnsThePolitiqueValueWhenNoDerogationExists() {
        List<Cellule> hierarchie = uneHierarchieA3Niveaux("RCCM-POL-002");
        Cellule universite = hierarchie.get(0);
        Cellule departement = hierarchie.get(2);

        Politique p = politiqueService.creer(new CreerPolitiqueCommand(
                universite.organisationId(), ContexteNature.ORGANISATION, DomainePolitique.SECURITE_MOT_DE_PASSE,
                "Longueur minimale", "12 caracteres", LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        ValeurEffective resolue = politiqueService.resoudrePourCellule(p.id(), departement.id());

        assertEquals("12 caracteres", resolue.valeur());
        assertFalse(resolue.resulteDuneDerogation());
    }

    /**
     * Le test central : une Derogation posee sur la Faculte (niveau intermediaire)
     * doit s'appliquer au Departement (son enfant direct), meme si l'Universite
     * (l'ancetre le plus eloigne) n'a aucune Derogation. La regle "le plus proche
     * l'emporte" est verifiee en conditions reelles, via la Fermeture Transitive.
     */
    @Test
    @TestTransaction
    void resoudrePourCelluleAppliesTheNearestDerogationInTheTree() {
        List<Cellule> hierarchie = uneHierarchieA3Niveaux("RCCM-POL-003");
        Cellule universite = hierarchie.get(0);
        Cellule faculte = hierarchie.get(1);
        Cellule departement = hierarchie.get(2);

        Politique p = politiqueService.creer(new CreerPolitiqueCommand(
                universite.organisationId(), ContexteNature.ORGANISATION, DomainePolitique.SECURITE_MOT_DE_PASSE,
                "Longueur minimale", "12 caracteres", LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        derogationService.creer(new CreerDerogationCommand(
                p.id(), faculte.id(), "8 caracteres", "Contrainte technique du systeme de la Faculte",
                LocalDate.of(2025, 1, 1), null, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        ValeurEffective resoluePourDepartement = politiqueService.resoudrePourCellule(p.id(), departement.id());
        assertEquals("8 caracteres", resoluePourDepartement.valeur());
        assertTrue(resoluePourDepartement.resulteDuneDerogation());

        // L'Universite elle-meme, qui n'a pas de Derogation directe et n'est ancetre de personne
        // ici, doit recevoir la valeur brute de la Politique.
        ValeurEffective resoluePourUniversite = politiqueService.resoudrePourCellule(p.id(), universite.id());
        assertEquals("12 caracteres", resoluePourUniversite.valeur());
        assertFalse(resoluePourUniversite.resulteDuneDerogation());
    }

    @Test
    @TestTransaction
    void resoudrePourCelluleIgnoresAnExpiredDerogation() {
        List<Cellule> hierarchie = uneHierarchieA3Niveaux("RCCM-POL-004");
        Cellule universite = hierarchie.get(0);
        Cellule departement = hierarchie.get(2);

        Politique p = politiqueService.creer(new CreerPolitiqueCommand(
                universite.organisationId(), ContexteNature.ORGANISATION, DomainePolitique.SECURITE_MOT_DE_PASSE,
                "Longueur minimale", "12 caracteres", LocalDate.of(2025, 1, 1),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        derogationService.creer(new CreerDerogationCommand(
                p.id(), departement.id(), "8 caracteres", "Derogation temporaire expiree",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31),
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        ValeurEffective resolue = politiqueService.resoudrePourCellule(p.id(), departement.id());

        assertEquals("12 caracteres", resolue.valeur());
        assertFalse(resolue.resulteDuneDerogation());
    }
}
