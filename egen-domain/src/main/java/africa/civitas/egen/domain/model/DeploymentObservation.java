package africa.civitas.egen.domain.model;

import java.time.Instant;

/**
 * DTO neutre representant l'etat observe cote deploiement — ne contient
 * AUCUN type Nomad (voir docs/architecture/07-ports-et-adapters.md,
 * "DeploymentPort"). Reconstruit a chaque cycle de reconciliation, jamais
 * persiste comme verite (voir docs/architecture/04-moteur-de-reconciliation.md).
 */
public record DeploymentObservation(int desiredCount, int runningCount, int healthyCount,
                                     int failedCount, Instant lastUpdateTime) {

    public DeploymentObservation {
        if (desiredCount < 0 || runningCount < 0 || healthyCount < 0 || failedCount < 0) {
            throw new IllegalArgumentException(
                    "Les compteurs d'une DeploymentObservation ne peuvent pas etre negatifs");
        }
    }

    public static DeploymentObservation empty() {
        return new DeploymentObservation(0, 0, 0, 0, Instant.EPOCH);
    }

    public boolean isConverged() {
        return healthyCount >= desiredCount;
    }
}
