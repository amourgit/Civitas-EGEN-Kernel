package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.workflow.WorkflowId;

/** Aucune WorkflowDefinition n'a ete declaree avec cet identifiant. */
public final class WorkflowNotFoundException extends RuntimeException {
    public WorkflowNotFoundException(WorkflowId id) {
        super("Aucune definition de workflow avec l'identifiant : " + id);
    }
}
