package africa.civitas.egen.domain.workflow;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Un cycle entre les {@code dependsOn} des etapes d'une
 * {@link WorkflowDefinition} est toujours une erreur de conception,
 * jamais un cas a supporter — meme raisonnement que
 * {@link africa.civitas.egen.domain.dependency.CyclicDependencyException}
 * pour le graphe de dependances entre services.
 */
public final class CyclicStepDependencyException extends IllegalArgumentException {

    public CyclicStepDependencyException(List<StepId> cyclePath) {
        super("Cycle detecte entre les etapes du workflow : " + cyclePath.stream()
                .map(StepId::value)
                .collect(Collectors.joining(" -> ")));
    }
}
