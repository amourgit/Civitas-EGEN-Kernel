package africa.civitas.egen.domain.workflow;

import africa.civitas.egen.domain.model.ServiceId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduit, pour les etapes d'un workflow, exactement la meme rigueur que
 * {@code DependencyGraphTest} applique deja au graphe de dependances entre
 * services (meme algorithme, meme discipline de rejet a la construction).
 */
class WorkflowDefinitionTest {

    private static final ServiceId NEWS = ServiceId.of("news-service");
    private static final ExecutionPolicy POLICY = ExecutionPolicy.sequential(Duration.ofMinutes(5));

    private static Step step(String id, String... dependsOn) {
        return new Step(StepId.of(id), NEWS, StepOperation.parse("POST /internal/op"),
                List.of(dependsOn).stream().map(StepId::of).toList(),
                Duration.ofSeconds(10), null, null);
    }

    @Test
    void buildsAValidLinearWorkflow() {
        WorkflowDefinition definition = WorkflowDefinition.of(WorkflowId.of("publish-article-workflow"),
                List.of(step("validate-content"), step("reserve-slot", "validate-content"),
                        step("publish", "reserve-slot")),
                CompensationStrategy.BACKWARD, POLICY);

        assertEquals(3, definition.steps().size());
        assertEquals(List.of(StepId.of("validate-content"), StepId.of("reserve-slot"), StepId.of("publish")),
                definition.topologicalOrder());
    }

    @Test
    void rejectsAnEmptyStepList() {
        assertThrows(IllegalArgumentException.class,
                () -> WorkflowDefinition.of(WorkflowId.of("empty"), List.of(), CompensationStrategy.BACKWARD, POLICY));
    }

    @Test
    void rejectsDuplicateStepIds() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> WorkflowDefinition.of(WorkflowId.of("dup"),
                        List.of(step("a"), step("a")), CompensationStrategy.BACKWARD, POLICY));
        assertTrue(ex.getMessage().contains("a"));
    }

    @Test
    void rejectsADependsOnReferencingAnUnknownStep() {
        assertThrows(IllegalArgumentException.class,
                () -> WorkflowDefinition.of(WorkflowId.of("dangling"),
                        List.of(step("a", "ghost")), CompensationStrategy.BACKWARD, POLICY));
    }

    @Test
    void rejectsACycleBetweenSteps() {
        CyclicStepDependencyException ex = assertThrows(CyclicStepDependencyException.class,
                () -> WorkflowDefinition.of(WorkflowId.of("cyclic"),
                        List.of(step("a", "c"), step("b", "a"), step("c", "b")),
                        CompensationStrategy.BACKWARD, POLICY));
        assertTrue(ex.getMessage().contains("->"));
    }

    @Test
    void rejectsAManualApprovalStepReferencingAnUnknownStep() {
        ExecutionPolicy policyWithGhostApproval =
                new ExecutionPolicy(Duration.ofMinutes(5), List.of(StepId.of("ghost")), 1);

        assertThrows(IllegalArgumentException.class,
                () -> WorkflowDefinition.of(WorkflowId.of("ghost-approval"),
                        List.of(step("a")), CompensationStrategy.BACKWARD, policyWithGhostApproval));
    }

    @Test
    void aStepCannotDependOnItself() {
        assertThrows(IllegalArgumentException.class, () -> step("a", "a"));
    }

    @Test
    void stepByIdFindsAKnownStepAndIsEmptyForAnUnknownOne() {
        WorkflowDefinition definition = WorkflowDefinition.of(WorkflowId.of("single"),
                List.of(step("only")), CompensationStrategy.BACKWARD, POLICY);

        assertTrue(definition.stepById(StepId.of("only")).isPresent());
        assertTrue(definition.stepById(StepId.of("unknown")).isEmpty());
    }
}
