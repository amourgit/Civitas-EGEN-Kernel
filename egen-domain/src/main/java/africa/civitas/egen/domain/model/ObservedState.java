package africa.civitas.egen.domain.model;

/**
 * Etat observe reel, reconstruit a CHAQUE cycle de reconciliation — jamais
 * persiste comme verite, c'est un cache de lecture (voir
 * docs/architecture/04-moteur-de-reconciliation.md).
 *
 * <p>Ne porte pour l'instant que l'observation de deploiement : les
 * observations de discovery et de messaging rejoignent cette classe aux
 * phases qui les introduisent (Discovery en Phase 2, Messaging en Phase 3 —
 * voir docs/architecture/19-feuille-de-route.md), jamais par anticipation.</p>
 */
public record ObservedState(DeploymentObservation deployment, long observedGeneration) {

    public ObservedState {
        if (deployment == null) {
            throw new IllegalArgumentException("ObservedState.deployment est obligatoire");
        }
        if (observedGeneration < 0) {
            throw new IllegalArgumentException(
                    "ObservedState.observedGeneration ne peut pas etre negative");
        }
    }
}
