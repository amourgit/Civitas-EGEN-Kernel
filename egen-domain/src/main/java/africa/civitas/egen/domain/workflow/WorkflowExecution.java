package africa.civitas.egen.domain.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Aggregate root — voir docs/architecture/05-modele-de-domaine.md
 * ({@code WorkflowExecution}). Immuable, comme {@link
 * africa.civitas.egen.domain.lifecycle.ServiceStatus} : chaque transition
 * produit une NOUVELLE instance plutot que de muter l'existante — a charge
 * de l'appelant (le moteur, en egen-application) de persister la nouvelle
 * instance via {@code WorkflowStorePort} (voir
 * docs/architecture/16-packages-et-stack-technique.md).
 *
 * <p>{@link #stepResults()} est la liste append-only mentionnee en
 * docs/architecture/11-moteur-de-workflow.md ("Durabilite de l'execution") :
 * elle ne contient QUE le dernier {@link StepResult} par (etape, tentative)
 * — {@link #withStepResult} ajoute toujours, ne remplace jamais une entree
 * existante.</p>
 */
public final class WorkflowExecution {

    private static final WorkflowExecutionStateMachine STATE_MACHINE = new WorkflowExecutionStateMachine();

    private final WorkflowId workflowId;
    private final ExecutionId executionId;
    private final WorkflowExecutionStatus status;
    private final List<StepResult> stepResults;
    private final Instant startedAt;
    private final Map<String, String> input;

    private WorkflowExecution(WorkflowId workflowId, ExecutionId executionId, WorkflowExecutionStatus status,
                               List<StepResult> stepResults, Instant startedAt, Map<String, String> input) {
        this.workflowId = workflowId;
        this.executionId = executionId;
        this.status = status;
        this.stepResults = stepResults;
        this.startedAt = startedAt;
        this.input = input;
    }

    /** Demarre une nouvelle execution sans entree, sans aucun resultat d'etape encore. */
    public static WorkflowExecution start(WorkflowId workflowId, ExecutionId executionId, Instant startedAt) {
        return start(workflowId, executionId, startedAt, Map.of());
    }

    /**
     * Demarre une nouvelle execution en memorisant son entree
     * ({@link WorkflowInput#values()}) : necessaire pour qu'une reprise
     * apres redemarrage du Kernel rejoue les memes payloads (voir
     * docs/architecture/11-moteur-de-workflow.md, "Durabilite de l'execution").
     * L'entree est immuable pour toute la vie de l'execution.
     */
    public static WorkflowExecution start(WorkflowId workflowId, ExecutionId executionId, Instant startedAt,
                                           Map<String, String> input) {
        if (workflowId == null) {
            throw new IllegalArgumentException("WorkflowExecution.workflowId est obligatoire");
        }
        if (executionId == null) {
            throw new IllegalArgumentException("WorkflowExecution.executionId est obligatoire");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("WorkflowExecution.startedAt est obligatoire");
        }
        return new WorkflowExecution(workflowId, executionId, WorkflowExecutionStatus.RUNNING, List.of(), startedAt,
                input == null ? Map.of() : Map.copyOf(input));
    }

    /**
     * Reconstruit une execution dans un etat DEJA connu comme valide (lu
     * depuis la persistance) — contourne deliberement la validation de
     * {@link WorkflowExecutionStateMachine} : il ne s'agit pas d'une
     * TRANSITION mais de la restitution d'un fait historique deja valide
     * au moment ou il a ete persiste (voir
     * docs/architecture/11-moteur-de-workflow.md, "Durabilite de
     * l'execution"). Reserve aux adapters {@code WorkflowStorePort} —
     * jamais utilise par le moteur d'orchestration lui-meme, qui doit
     * toujours passer par {@link #withStatus} pour une VRAIE transition.
     */
    public static WorkflowExecution rehydrate(WorkflowId workflowId, ExecutionId executionId,
                                               WorkflowExecutionStatus status, List<StepResult> stepResults,
                                               Instant startedAt) {
        return rehydrate(workflowId, executionId, status, stepResults, startedAt, Map.of());
    }

    /** Variante restituant aussi l'entree memorisee au demarrage (voir {@link #start(WorkflowId, ExecutionId, Instant, Map)}). */
    public static WorkflowExecution rehydrate(WorkflowId workflowId, ExecutionId executionId,
                                               WorkflowExecutionStatus status, List<StepResult> stepResults,
                                               Instant startedAt, Map<String, String> input) {
        if (workflowId == null) {
            throw new IllegalArgumentException("WorkflowExecution.workflowId est obligatoire");
        }
        if (executionId == null) {
            throw new IllegalArgumentException("WorkflowExecution.executionId est obligatoire");
        }
        if (status == null) {
            throw new IllegalArgumentException("WorkflowExecution.status est obligatoire");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("WorkflowExecution.startedAt est obligatoire");
        }
        List<StepResult> copy = stepResults == null ? List.of() : List.copyOf(stepResults);
        return new WorkflowExecution(workflowId, executionId, status, copy, startedAt,
                input == null ? Map.of() : Map.copyOf(input));
    }

    public WorkflowId workflowId() {
        return workflowId;
    }

    public ExecutionId executionId() {
        return executionId;
    }

    public WorkflowExecutionStatus status() {
        return status;
    }

    public List<StepResult> stepResults() {
        return stepResults;
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** Entree memorisee au demarrage — immuable, jamais interpretee par EGEN. */
    public Map<String, String> input() {
        return input;
    }

    /**
     * Le {@link StepResult} le plus RECENT pour cette etape (une etape
     * retentee plusieurs fois a plusieurs entrees dans {@link #stepResults()},
     * seule la derniere refletant l'etat courant).
     */
    public Optional<StepResult> latestResultFor(StepId stepId) {
        StepResult latest = null;
        for (StepResult result : stepResults) {
            if (result.stepId().equals(stepId)) {
                latest = result;
            }
        }
        return Optional.ofNullable(latest);
    }

    public boolean hasSucceeded(StepId stepId) {
        return latestResultFor(stepId).map(r -> r.status() == StepStatus.SUCCEEDED).orElse(false);
    }

    /**
     * Les etapes ayant reussi, dans leur ORDRE D'EXECUTION reel (celui de
     * {@link #stepResults()}, append-only) — c'est exactement l'ordre
     * inverse dans lequel une compensation {@link CompensationStrategy#BACKWARD}
     * doit les traiter (voir
     * docs/architecture/11-moteur-de-workflow.md).
     */
    public List<StepId> succeededStepsInExecutionOrder() {
        List<StepId> succeeded = new ArrayList<>();
        for (StepResult result : stepResults) {
            if (result.status() == StepStatus.SUCCEEDED && !succeeded.contains(result.stepId())) {
                succeeded.add(result.stepId());
            }
        }
        return List.copyOf(succeeded);
    }

    /** Ajoute un resultat a la liste append-only — ne remplace jamais une entree existante. */
    public WorkflowExecution withStepResult(StepResult result) {
        if (result == null) {
            throw new IllegalArgumentException("StepResult ne peut pas etre null");
        }
        List<StepResult> updated = new ArrayList<>(stepResults);
        updated.add(result);
        return new WorkflowExecution(workflowId, executionId, status, List.copyOf(updated), startedAt, input);
    }

    /**
     * @throws IllegalExecutionTransitionException si la transition n'est pas autorisee
     *         (voir {@link WorkflowExecutionStateMachine})
     */
    public WorkflowExecution withStatus(WorkflowExecutionStatus newStatus) {
        STATE_MACHINE.assertTransitionAllowed(status, newStatus);
        return new WorkflowExecution(workflowId, executionId, newStatus, stepResults, startedAt, input);
    }
}
