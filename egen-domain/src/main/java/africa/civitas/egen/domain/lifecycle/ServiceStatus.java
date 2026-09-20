package africa.civitas.egen.domain.lifecycle;

import java.util.List;

/**
 * Statut expose via l'API, ecrit par le reconciliateur — jamais par le
 * client (voir docs/architecture/04-moteur-de-reconciliation.md).
 * {@code observedGeneration} permet a un appelant de savoir si le statut
 * qu'il lit correspond a la derniere version qu'il a declaree.
 */
public record ServiceStatus(Phase phase, List<Condition> conditions, long observedGeneration) {

    public ServiceStatus {
        if (phase == null) {
            throw new IllegalArgumentException("ServiceStatus.phase est obligatoire");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        if (observedGeneration < 0) {
            throw new IllegalArgumentException(
                    "ServiceStatus.observedGeneration ne peut pas etre negative");
        }
    }

    public static ServiceStatus initial() {
        return new ServiceStatus(Phase.DECLARED, List.of(), 0L);
    }
}
