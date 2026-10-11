package africa.civitas.egen.application.workflow;

import africa.civitas.egen.domain.workflow.ExecutionId;

/** Aucune execution de workflow n'existe pour cet identifiant. */
public final class ExecutionNotFoundException extends RuntimeException {
    public ExecutionNotFoundException(ExecutionId id) {
        super("Aucune execution de workflow avec l'identifiant : " + id);
    }
}
