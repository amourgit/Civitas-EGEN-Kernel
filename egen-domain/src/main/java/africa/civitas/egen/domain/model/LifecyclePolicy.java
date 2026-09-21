package africa.civitas.egen.domain.model;

import java.time.Duration;

/**
 * Politique de cycle de vie d'un service (voir
 * docs/architecture/06-service-manifest.md, section "lifecycle"). Seul le
 * delai d'arret gracieux est necessaire pour l'instant (voir
 * docs/architecture/09-cycle-de-vie.md, "Ordre d'arret gracieux") ;
 * startupOrder et manualApprovalRequired rejoignent cette classe quand la
 * gestion des dependances (Phase 3) et l'approbation manuelle (Phase 4) sont
 * construites — voir docs/architecture/19-feuille-de-route.md.
 */
public record LifecyclePolicy(Duration shutdownGracePeriod) {

    public LifecyclePolicy {
        if (shutdownGracePeriod == null || shutdownGracePeriod.isNegative()) {
            throw new IllegalArgumentException(
                    "LifecyclePolicy.shutdownGracePeriod ne peut pas etre negatif");
        }
    }

    public static LifecyclePolicy defaultPolicy() {
        return new LifecyclePolicy(Duration.ofSeconds(30));
    }
}
