package africa.civitas.egen.domain.model;

/**
 * DTO neutre representant l'etat observe cote decouverte — ne contient
 * AUCUN type Consul (voir docs/architecture/07-ports-et-adapters.md,
 * "Discovery Port"). Reconstruit a chaque cycle de reconciliation, jamais
 * persiste comme verite (voir docs/architecture/04-moteur-de-reconciliation.md).
 */
public record DiscoveryObservation(int healthyInstanceCount) {

    public DiscoveryObservation {
        if (healthyInstanceCount < 0) {
            throw new IllegalArgumentException(
                    "DiscoveryObservation.healthyInstanceCount ne peut pas etre negatif");
        }
    }

    public static DiscoveryObservation empty() {
        return new DiscoveryObservation(0);
    }
}
