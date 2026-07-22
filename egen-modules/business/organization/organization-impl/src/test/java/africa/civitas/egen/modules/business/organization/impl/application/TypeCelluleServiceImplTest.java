package africa.civitas.egen.modules.business.organization.impl.application;

import africa.civitas.egen.modules.business.organization.api.command.CreerLexiqueOrganisationnelCommand;
import africa.civitas.egen.modules.business.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.modules.business.organization.api.command.CreerTypeCelluleCommand;
import africa.civitas.egen.modules.business.organization.api.domain.LexiqueOrganisationnel;
import africa.civitas.egen.modules.business.organization.api.domain.Organisation;
import africa.civitas.egen.modules.business.organization.api.domain.TypeCellule;
import africa.civitas.egen.modules.business.organization.api.domain.TypeOrganisation;
import africa.civitas.egen.modules.business.organization.api.exception.LexiqueIntrouvableException;
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
class TypeCelluleServiceImplTest {

    @Inject
    OrganisationServiceImpl organisationService;

    @Inject
    LexiqueOrganisationnelServiceImpl lexiqueService;

    @Inject
    TypeCelluleServiceImpl service;

    private LexiqueOrganisationnel unLexique(String identifiantJuridique) {
        Organisation o = organisationService.creer(new CreerOrganisationCommand(
                "Ministere de l'Education", "MINEDUC", TypeOrganisation.MINISTERE, "Education",
                "GA", identifiantJuridique, "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        return lexiqueService.creer(new CreerLexiqueOrganisationnelCommand(
                o.id(), "Lexique Academique", "Description", null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
    }

    @Test
    @TestTransaction
    void creerARootTypeSucceeds() {
        LexiqueOrganisationnel l = unLexique("RCCM-TC-001");

        TypeCellule racine = service.creer(new CreerTypeCelluleCommand(
                l.id(), "Etablissement", "Racine", 0, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertTrue(racine.sansContrainteDeParent());
    }

    @Test
    @TestTransaction
    void creerAChildTypeWithValidAllowedParentSucceeds() {
        LexiqueOrganisationnel l = unLexique("RCCM-TC-002");
        TypeCellule racine = service.creer(new CreerTypeCelluleCommand(
                l.id(), "Etablissement", "Racine", 0, null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        TypeCellule enfant = service.creer(new CreerTypeCelluleCommand(
                l.id(), "Faculte", "Description", 1, List.of(racine.id()), null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        assertEquals(List.of(racine.id()), enfant.typesParentsAutorisesIds());
    }

    @Test
    @TestTransaction
    void creerRejectsAnUnknownLexique() {
        assertThrows(LexiqueIntrouvableException.class, () -> service.creer(
                new CreerTypeCelluleCommand(
                        UUID.randomUUID(), "Faculte", "Description", 1, null, null,
                        Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE)));
    }
}
