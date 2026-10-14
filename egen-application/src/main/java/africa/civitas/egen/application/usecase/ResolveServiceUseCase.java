package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.model.ServiceId;

/**
 * Port primaire — resolution d'un service pour un appelant (SDK, sidecar,
 * systeme externe ; voir docs/architecture/12-communication-fabric.md et
 * 13-api-et-contrats.md, {@code /api/v1/discover/{serviceName}}). Lecture
 * seule : ne declenche ni reconciliation ni deploiement.
 */
public interface ResolveServiceUseCase {

    /**
     * Instances saines (possiblement aucune) et politique de resilience de la
     * cible. Un service inconnu du Registry mais connu du Discovery recoit
     * {@link africa.civitas.egen.domain.model.ResiliencyPolicy#defaults()}.
     */
    ServiceResolution resolve(ServiceId id);
}
