package africa.civitas.egen.domain.workflow;

import java.time.Duration;
import java.util.List;

/**
 * Voir docs/architecture/11-moteur-de-workflow.md, exemple YAML
 * ({@code executionPolicy: { globalTimeout, manualApprovalSteps }}) et
 * docs/architecture/05-modele-de-domaine.md,
 * {@code ExecutionPolicy (timeout, retry, parallelisme autorise)}.
 *
 * @param manualApprovalSteps etapes qui, une fois leurs dependances
 *                            satisfaites, attendent une confirmation
 *                            humaine explicite avant de s'executer —
 *                            reference des {@link StepId} de la meme
 *                            {@link WorkflowDefinition} (verifie par elle,
 *                            pas ici).
 * @param maxParallelSteps    nombre maximal d'etapes dont les dependances
 *                            sont satisfaites qu'un moteur peut executer
 *                            SIMULTANEMENT (voir
 *                            docs/architecture/05-modele-de-domaine.md,
 *                            "parallelisme autorise" — non illustre dans
 *                            l'exemple YAML de 11, qui est purement
 *                            sequentiel ; {@code 1} reproduit ce
 *                            comportement par defaut, strictement
 *                            sequentiel, jamais une valeur plus permissive
 *                            choisie par anticipation).
 */
public record ExecutionPolicy(Duration globalTimeout, List<StepId> manualApprovalSteps, int maxParallelSteps) {

    public ExecutionPolicy {
        if (globalTimeout == null || globalTimeout.isNegative() || globalTimeout.isZero()) {
            throw new IllegalArgumentException("ExecutionPolicy.globalTimeout doit etre strictement positif");
        }
        manualApprovalSteps = manualApprovalSteps == null ? List.of() : List.copyOf(manualApprovalSteps);
        if (maxParallelSteps < 1) {
            throw new IllegalArgumentException("ExecutionPolicy.maxParallelSteps doit etre >= 1");
        }
    }

    /** Convenience : politique sequentielle (maxParallelSteps=1), sans etape a validation manuelle. */
    public static ExecutionPolicy sequential(Duration globalTimeout) {
        return new ExecutionPolicy(globalTimeout, List.of(), 1);
    }
}
