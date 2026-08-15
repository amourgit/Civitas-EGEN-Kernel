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
    void aContexteWithNoActivationAtAllIsDeniedByDefault() {
        DecisionNoyau decision = resolver.estActifPour(UUID.randomUUID(), new ModuleId("academie"));

        assertFalse(decision.autorise());
    }

    @Test
    @TestTransaction
    void aContexteWithAnActiveActivationIsAuthorized() {
        ModuleId moduleId = new ModuleId("academie");
        catalogueService.enregistrer(new EnregistrerModuleCommand(
                moduleId, "Academie", "description", Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        UUID contexteSouscripteurId = UUID.randomUUID();
        UUID contexteCibleId = UUID.randomUUID();
        souscriptionService.souscrire(new SouscrireModuleCommand(
                contexteSouscripteurId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        activationService.activer(new ActiverModuleCommand(
                contexteSouscripteurId, contexteCibleId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));

        DecisionNoyau decision = resolver.estActifPour(contexteCibleId, moduleId);

        assertTrue(decision.autorise());
    }

    @Test
    @TestTransaction
    void aDeactivatedModuleIsDeniedAgain() {
        ModuleId moduleId = new ModuleId("academie");
        catalogueService.enregistrer(new EnregistrerModuleCommand(
                moduleId, "Academie", "description", Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        UUID contexteSouscripteurId = UUID.randomUUID();
        UUID contexteCibleId = UUID.randomUUID();
        souscriptionService.souscrire(new SouscrireModuleCommand(
                contexteSouscripteurId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        Activation activation = activationService.activer(new ActiverModuleCommand(
                contexteSouscripteurId, contexteCibleId, moduleId, Acteur.systeme("test"), OrigineDonnee.SAISIE_MANUELLE));
        activationService.desactiver(new DesactiverModuleCommand(
                activation.id(), Acteur.systeme("test"), "retrait"));

        DecisionNoyau decision = resolver.estActifPour(contexteCibleId, moduleId);

        assertFalse(decision.autorise());
    }
}
