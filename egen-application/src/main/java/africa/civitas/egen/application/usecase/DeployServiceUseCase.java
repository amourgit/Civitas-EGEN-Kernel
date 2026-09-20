package africa.civitas.egen.application.usecase;

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
}
