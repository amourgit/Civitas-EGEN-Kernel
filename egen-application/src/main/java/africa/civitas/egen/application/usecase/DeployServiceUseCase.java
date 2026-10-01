package africa.civitas.egen.application.usecase;

import africa.civitas.egen.application.port.TraceContext;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.TargetEnvironment;

/**
 * Port primaire — declenche par l'adapter primaire REST/gRPC (egen-api) sur
 * "Declare" (voir docs/architecture/02-principes-fondamentaux.md, cycle
 * canonique). Valide et persiste l'etat desire, PUIS enqueue la
 * reconciliation — ne delegue jamais directement a un DeploymentPort
 * (docs/architecture/04-moteur-de-reconciliation.md, "reconciliation
 * synchrone dans l'API").
 */
public interface DeployServiceUseCase {

    DeclareResult declare(ServiceManifest manifest, TargetEnvironment targetEnvironment);

    /**
     * Variante propageant le contexte de trace de l'appel HTTP entrant
     * (voir egen-api.rest.ServiceResource) jusqu'au premier cycle de
     * reconciliation qui en resulte — voir
     * docs/architecture/15-observabilite.md et
     * africa.civitas.egen.application.reconciliation.PendingDeclareTraces.
     * Par defaut, delegue simplement a {@link #declare(ServiceManifest, TargetEnvironment)}
     * sans propager de trace : une implementation qui n'a pas besoin de ce
     * lien (tests, usages internes) n'a rien a modifier.
     */
    default DeclareResult declare(ServiceManifest manifest, TargetEnvironment targetEnvironment,
                                   TraceContext triggeringTrace) {
        return declare(manifest, targetEnvironment);
    }
}
