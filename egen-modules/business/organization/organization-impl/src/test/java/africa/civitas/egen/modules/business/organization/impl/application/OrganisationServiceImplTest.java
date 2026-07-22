package africa.civitas.egen.modules.business.organization.impl.application;

import africa.civitas.egen.modules.business.organization.api.command.CreerOrganisationCommand;
import africa.civitas.egen.modules.business.organization.api.domain.Organisation;
import africa.civitas.egen.modules.business.organization.api.domain.StatutOrganisation;
import africa.civitas.egen.modules.business.organization.api.domain.TypeOrganisation;
import africa.civitas.egen.modules.business.organization.api.exception.OrganisationnelConflitException;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class OrganisationServiceImplTest {

    @Inject
    OrganisationServiceImpl service;

    private static CreerOrganisationCommand commande(String identifiantJuridique) {
        return new CreerOrganisationCommand(
                "Ministere de l'Education", "MINEDUC", TypeOrganisation.MINISTERE, "Education",
                "GA", identifiantJuridique, "XAF", List.of("fr"), "Africa/Libreville", null, null,
                Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE);
    }

    @Test
    @TestTransaction
    void creerPersistsAsActifByDefault() {
        Organisation o = service.creer(commande("RCCM-TEST-001"));

        assertEquals(StatutOrganisation.ACTIF, o.statut());
    }

    @Test
    @TestTransaction
    void creerRejectsADuplicateIdentifiantJuridique() {
        service.creer(commande("RCCM-TEST-002"));

        assertThrows(OrganisationnelConflitException.class, () -> service.creer(commande("RCCM-TEST-002")));
    }
}
