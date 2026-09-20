package africa.civitas.egen.domain.lifecycle;

import java.time.Instant;

/**
 * Element de statut fin-grained, complementaire a la {@link Phase} macro —
 * voir docs/architecture/09-cycle-de-vie.md, "Conditions".
 */
public record Condition(String type, ConditionStatus status, String reason, String message,
                         Instant lastTransitionTime) {

    public Condition {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Condition.type ne peut pas etre vide");
        }
        if (status == null) {
            throw new IllegalArgumentException("Condition.status est obligatoire");
        }
        if (lastTransitionTime == null) {
            throw new IllegalArgumentException("Condition.lastTransitionTime est obligatoire");
        }
    }
}
