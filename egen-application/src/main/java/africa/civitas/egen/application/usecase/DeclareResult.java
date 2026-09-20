package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.model.ServiceId;

/**
 * Retour immediat d'une declaration — voir
 * docs/architecture/04-moteur-de-reconciliation.md, "Anti-pattern a bannir" :
 * l'API repond 202 Accepted avec cette generation, sans jamais attendre la
 * convergence du deploiement.
 */
public record DeclareResult(ServiceId serviceId, long generation) {
}
