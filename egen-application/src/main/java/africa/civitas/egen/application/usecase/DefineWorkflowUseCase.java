package africa.civitas.egen.application.usecase;

import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowId;

/**
 * Port primaire — declaration et lecture d'une {@link WorkflowDefinition}
 * (voir docs/architecture/13-api-et-contrats.md, {@code /api/v1/workflows}).
 * La definition est deja validee a sa construction (cycles, etapes inconnues,
 * voir docs/architecture/11) : ce use case ne re-valide jamais, il persiste.
 * Declarer deux fois le meme identifiant remplace la definition (upsert).
 */
public interface DefineWorkflowUseCase {

    WorkflowDefinition define(WorkflowDefinition definition);

    /** @throws WorkflowNotFoundException si aucune definition n'existe pour cet identifiant */
    WorkflowDefinition get(WorkflowId id);
}
