package africa.civitas.egen.kernel.moduleregistry.application;

import africa.civitas.egen.kernel.domain.module.Activation;
import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.moduleregistry.command.ActiverModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.command.DesactiverModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.command.EnregistrerModuleCommand;
import africa.civitas.egen.kernel.moduleregistry.command.SouscrireModuleCommand;
import africa.civitas.egen.kernel.sdk.permission.authorization.DecisionNoyau;
import africa.civitas.egen.kernel.sdk.tracabilite.Acteur;
import africa.civitas.egen.kernel.sdk.tracabilite.OrigineDonnee;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ModuleActivationResolverImplTest {

    @Inject
    CatalogueServiceImpl catalogueService;

    @Inject
    SouscriptionServiceImpl souscriptionService;

    @Inject
    ActivationServiceImpl activationService;

    @Inject
    ModuleActivationResolverImpl resolver;

    @Test
    @TestTransaction
    void aCelluleWithNoActivationAtAllIsDeniedByDefault() {
        DecisionNoyau decision = resolver.estActifPour(UUID.randomUUID(), new ModuleId("academie"));

        assertFalse(decision.autorise());
    }

    @Test
    @TestTransaction
    void aCelluleWithAnActiveActivationIsAuthorized() {
        ModuleId moduleId = new ModuleId("academie");
        catalogueService.enregistrer(new EnregistrerModuleCommand(
                moduleId, "Academie", "description", Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        UUID organisationId = UUID.randomUUID();
        UUID celluleId = UUID.randomUUID();
        souscriptionService.souscrire(new SouscrireModuleCommand(
                organisationId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        activationService.activer(new ActiverModuleCommand(
                organisationId, celluleId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        DecisionNoyau decision = resolver.estActifPour(celluleId, moduleId);

        assertTrue(decision.autorise());
    }

    @Test
    @TestTransaction
    void aDeactivatedModuleIsDeniedAgain() {
        ModuleId moduleId = new ModuleId("academie");
        catalogueService.enregistrer(new EnregistrerModuleCommand(
                moduleId, "Academie", "description", Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        UUID organisationId = UUID.randomUUID();
        UUID celluleId = UUID.randomUUID();
        souscriptionService.souscrire(new SouscrireModuleCommand(
                organisationId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        Activation activation = activationService.activer(new ActiverModuleCommand(
                organisationId, celluleId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        activationService.desactiver(new DesactiverModuleCommand(
                activation.id(), Acteur.systeme("test"), "retrait"));

        DecisionNoyau decision = resolver.estActifPour(celluleId, moduleId);

        assertFalse(decision.autorise());
    }
}
