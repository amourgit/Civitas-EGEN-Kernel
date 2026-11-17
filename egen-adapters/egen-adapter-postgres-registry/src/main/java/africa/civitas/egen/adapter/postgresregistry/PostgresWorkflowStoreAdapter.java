package africa.civitas.egen.adapter.postgresregistry;

import africa.civitas.egen.application.port.WorkflowStoreException;
import africa.civitas.egen.application.port.WorkflowStorePort;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.workflow.CompensationStrategy;
import africa.civitas.egen.domain.workflow.ExecutionId;
import africa.civitas.egen.domain.workflow.ExecutionPolicy;
import africa.civitas.egen.domain.workflow.RetryPolicy;
import africa.civitas.egen.domain.workflow.Step;
import africa.civitas.egen.domain.workflow.StepId;
import africa.civitas.egen.domain.workflow.StepOperation;
import africa.civitas.egen.domain.workflow.StepResult;
import africa.civitas.egen.domain.workflow.StepStatus;
import africa.civitas.egen.domain.workflow.WorkflowDefinition;
import africa.civitas.egen.domain.workflow.WorkflowExecution;
import africa.civitas.egen.domain.workflow.WorkflowExecutionStatus;
import africa.civitas.egen.domain.workflow.WorkflowId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation du {@link WorkflowStorePort} en PostgreSQL — voir
 * docs/architecture/11-moteur-de-workflow.md, "Durabilite de l'execution".
 * MEME base que {@link PostgresRegistryAdapter} (voir cette javadoc-la pour
 * le raisonnement : pas une base dediee, garde-fou n3,
 * docs/architecture/02-principes-fondamentaux.md), schema auto-cree au
 * premier usage comme son voisin.
 *
 * <p>{@code egen_workflow_step_result} est insert-only : chaque
 * {@code (execution_id, step_id, attempt)} est une ligne distincte,
 * jamais mise a jour — {@link #save} insere uniquement les lignes
 * absentes (idempotent : rappeler {@code save} avec le MEME
 * {@link WorkflowExecution} ne duplique rien), fidele a la discipline
 * append-only de {@code WorkflowExecution.stepResults()}. L'ordre
 * d'insertion reel (necessaire a
 * {@code WorkflowExecution.succeededStepsInExecutionOrder()}) est
 * preserve par la colonne auto-incrementee {@code insertion_order}.</p>
 */
public final class PostgresWorkflowStoreAdapter implements WorkflowStorePort {

    private final DataSource dataSource;
    private final ObjectMapper mapper = new ObjectMapper();

    public PostgresWorkflowStoreAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
        createSchemaIfAbsent();
    }

    // ------------------------------------------------------------------
    // WorkflowStorePort
    // ------------------------------------------------------------------

    @Override
    public WorkflowExecution save(WorkflowExecution execution) {
        String sql = "INSERT INTO egen_workflow_execution "
                + "(execution_id, workflow_id, status, started_at, input_json, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, now()) "
                + "ON CONFLICT (execution_id) DO UPDATE SET "
                + "status = EXCLUDED.status, updated_at = now()"; // input_json : immuable, jamais mis a jour
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, execution.executionId().value());
                statement.setString(2, execution.workflowId().value());
                statement.setString(3, execution.status().name());
                statement.setTimestamp(4, Timestamp.from(execution.startedAt()));
                statement.setString(5, writeJson(outputToJson(execution.input())));
                statement.executeUpdate();
            }
            insertNewStepResults(connection, execution);
            connection.commit();
            return execution;
        } catch (SQLException e) {
            throw new WorkflowStoreException(
                    "Echec de sauvegarde de WorkflowExecution " + execution.executionId(), e);
        }
    }

    @Override
    public Optional<WorkflowExecution> findById(ExecutionId id) {
        String sql = "SELECT workflow_id, status, started_at, input_json FROM egen_workflow_execution WHERE execution_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                WorkflowId workflowId = WorkflowId.of(resultSet.getString("workflow_id"));
                WorkflowExecutionStatus status = WorkflowExecutionStatus.valueOf(resultSet.getString("status"));
                Instant startedAt = resultSet.getTimestamp("started_at").toInstant();
                List<StepResult> stepResults = readStepResults(connection, id);
                Map<String, String> input = outputFromJson(readJson(resultSet.getString("input_json")));
                return Optional.of(WorkflowExecution.rehydrate(workflowId, id, status, stepResults, startedAt, input));
            }
        } catch (SQLException | IOException e) {
            throw new WorkflowStoreException("Echec de lecture de WorkflowExecution " + id, e);
        }
    }

    @Override
    public List<ExecutionId> findRunningExecutions() {
        // PAUSED est deliberement exclu : une execution en pause attend une
        // reprise EXPLICITE (resume()), jamais une reprise automatique au
        // redemarrage du Kernel — voir docs/architecture/11-moteur-de-workflow.md,
        // "Le port".
        String sql = "SELECT execution_id FROM egen_workflow_execution WHERE status IN ('RUNNING', 'COMPENSATING')";
        List<ExecutionId> ids = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                ids.add(ExecutionId.of(resultSet.getString("execution_id")));
            }
            return List.copyOf(ids);
        } catch (SQLException e) {
            throw new WorkflowStoreException("Echec de lecture des executions en cours", e);
        }
    }

    @Override
    public Optional<WorkflowDefinition> findDefinition(WorkflowId id) {
        String sql = "SELECT definition_json FROM egen_workflow_definition WHERE workflow_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(definitionFromJson(readJson(resultSet.getString("definition_json"))));
            }
        } catch (SQLException | IOException e) {
            throw new WorkflowStoreException("Echec de lecture de WorkflowDefinition " + id, e);
        }
    }

    @Override
    public WorkflowDefinition saveDefinition(WorkflowDefinition definition) {
        String sql = "INSERT INTO egen_workflow_definition (workflow_id, definition_json, updated_at) "
                + "VALUES (?, ?, now()) "
                + "ON CONFLICT (workflow_id) DO UPDATE SET "
                + "definition_json = EXCLUDED.definition_json, updated_at = now()";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, definition.id().value());
            statement.setString(2, writeJson(definitionToJson(definition)));
            statement.executeUpdate();
            return definition;
        } catch (SQLException e) {
            throw new WorkflowStoreException("Echec de sauvegarde de WorkflowDefinition " + definition.id(), e);
        }
    }

    // ------------------------------------------------------------------
    // StepResult — insert-only
    // ------------------------------------------------------------------

    private void insertNewStepResults(Connection connection, WorkflowExecution execution) throws SQLException {
        String sql = "INSERT INTO egen_workflow_step_result "
                + "(execution_id, step_id, attempt, status, started_at, completed_at, output_json, error_message) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (execution_id, step_id, attempt) DO NOTHING";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (StepResult result : execution.stepResults()) {
                statement.setString(1, execution.executionId().value());
                statement.setString(2, result.stepId().value());
                statement.setInt(3, result.attempt());
                statement.setString(4, result.status().name());
                statement.setTimestamp(5, Timestamp.from(result.startedAt()));
                statement.setTimestamp(6, result.completedAt() == null ? null : Timestamp.from(result.completedAt()));
                statement.setString(7, writeJson(outputToJson(result.output())));
                statement.setString(8, result.errorMessage());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<StepResult> readStepResults(Connection connection, ExecutionId executionId) throws SQLException {
        String sql = "SELECT step_id, attempt, status, started_at, completed_at, output_json, error_message "
                + "FROM egen_workflow_step_result WHERE execution_id = ? ORDER BY insertion_order ASC";
        List<StepResult> results = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, executionId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp completedAt = resultSet.getTimestamp("completed_at");
                    try {
                        results.add(new StepResult(
                                StepId.of(resultSet.getString("step_id")),
                                StepStatus.valueOf(resultSet.getString("status")),
                                resultSet.getInt("attempt"),
                                resultSet.getTimestamp("started_at").toInstant(),
                                completedAt == null ? null : completedAt.toInstant(),
                                outputFromJson(readJson(resultSet.getString("output_json"))),
                                resultSet.getString("error_message")));
                    } catch (IOException e) {
                        throw new WorkflowStoreException(
                                "StepResult illisible pour l'execution " + executionId, e);
                    }
                }
            }
        }
        return List.copyOf(results);
    }

    // ------------------------------------------------------------------
    // Schema
    // ------------------------------------------------------------------

    /**
     * Une seule instruction atomique : insere le bail s'il n'existe pas, ou le prend si le bail existant est expire
     * ({@code lease_expires_at <= now}) ou deja a ce pilote. Zero ligne touchee = un autre pilote le detient.
     */
    @Override
    public boolean tryClaim(ExecutionId id, String driverId, Instant now, Instant leaseExpiresAt) {
        String sql = "INSERT INTO egen_workflow_lease (execution_id, driver_id, lease_expires_at) VALUES (?, ?, ?) "
                + "ON CONFLICT (execution_id) DO UPDATE SET driver_id = EXCLUDED.driver_id, "
                + "lease_expires_at = EXCLUDED.lease_expires_at "
                + "WHERE egen_workflow_lease.lease_expires_at <= ? OR egen_workflow_lease.driver_id = EXCLUDED.driver_id";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            statement.setString(2, driverId);
            statement.setTimestamp(3, Timestamp.from(leaseExpiresAt));
            statement.setTimestamp(4, Timestamp.from(now));
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new WorkflowStoreException("Echec de prise du bail de l'execution " + id, e);
        }
    }

    /** Seul le detenteur dont le bail n'a pas expire peut le prolonger. */
    @Override
    public boolean renew(ExecutionId id, String driverId, Instant now, Instant leaseExpiresAt) {
        String sql = "UPDATE egen_workflow_lease SET lease_expires_at = ? "
                + "WHERE execution_id = ? AND driver_id = ? AND lease_expires_at > ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(leaseExpiresAt));
            statement.setString(2, id.value());
            statement.setString(3, driverId);
            statement.setTimestamp(4, Timestamp.from(now));
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new WorkflowStoreException("Echec de renouvellement du bail de l'execution " + id, e);
        }
    }

    @Override
    public void release(ExecutionId id, String driverId) {
        String sql = "DELETE FROM egen_workflow_lease WHERE execution_id = ? AND driver_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            statement.setString(2, driverId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new WorkflowStoreException("Echec de remise du bail de l'execution " + id, e);
        }
    }

    private void createSchemaIfAbsent() {
        String[] ddl = {
                "CREATE TABLE IF NOT EXISTS egen_workflow_definition ("
                        + "workflow_id VARCHAR(255) PRIMARY KEY, "
                        + "definition_json TEXT NOT NULL, "
                        + "updated_at TIMESTAMPTZ NOT NULL DEFAULT now())",
                // Bail de pilotage d'une execution (phase P0, etape P0-3b ; docs/architecture/11, « Bail d'execution »).
                "CREATE TABLE IF NOT EXISTS egen_workflow_lease ("
                        + "execution_id VARCHAR(255) PRIMARY KEY, "
                        + "driver_id VARCHAR(255) NOT NULL, "
                        + "lease_expires_at TIMESTAMPTZ NOT NULL)",
                "CREATE TABLE IF NOT EXISTS egen_workflow_execution ("
                        + "execution_id VARCHAR(255) PRIMARY KEY, "
                        + "workflow_id VARCHAR(255) NOT NULL, "
                        + "status VARCHAR(64) NOT NULL, "
                        + "started_at TIMESTAMPTZ NOT NULL, "
                        + "input_json TEXT NOT NULL DEFAULT '{}', "
                        + "updated_at TIMESTAMPTZ NOT NULL DEFAULT now())",
                // Bases creees avant l'ajout de input_json (Phase 5, increment 3) :
                "ALTER TABLE egen_workflow_execution ADD COLUMN IF NOT EXISTS input_json TEXT NOT NULL DEFAULT '{}'",
                "CREATE TABLE IF NOT EXISTS egen_workflow_step_result ("
                        + "insertion_order BIGSERIAL PRIMARY KEY, "
                        + "execution_id VARCHAR(255) NOT NULL, "
                        + "step_id VARCHAR(255) NOT NULL, "
                        + "attempt INT NOT NULL, "
                        + "status VARCHAR(64) NOT NULL, "
                        + "started_at TIMESTAMPTZ NOT NULL, "
                        + "completed_at TIMESTAMPTZ, "
                        + "output_json TEXT NOT NULL, "
                        + "error_message TEXT, "
                        + "UNIQUE (execution_id, step_id, attempt))",
        };
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : ddl) {
                statement.executeUpdate(sql);
            }
        } catch (SQLException e) {
            throw new WorkflowStoreException("Echec de creation du schema du Workflow Store", e);
        }
    }

    // ------------------------------------------------------------------
    // JSON — traduction manuelle et explicite (meme choix que
    // PostgresRegistryAdapter : controle total sur le mapping).
    // ------------------------------------------------------------------

    private ObjectNode definitionToJson(WorkflowDefinition definition) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", definition.id().value());
        node.put("compensationStrategy", definition.compensationStrategy().name());

        ObjectNode executionPolicy = node.putObject("executionPolicy");
        executionPolicy.put("globalTimeoutSeconds", definition.executionPolicy().globalTimeout().toSeconds());
        executionPolicy.put("maxParallelSteps", definition.executionPolicy().maxParallelSteps());
        ArrayNode approvalSteps = executionPolicy.putArray("manualApprovalSteps");
        definition.executionPolicy().manualApprovalSteps().forEach(stepId -> approvalSteps.add(stepId.value()));

        ArrayNode steps = node.putArray("steps");
        for (Step step : definition.steps()) {
            ObjectNode stepNode = steps.addObject();
            stepNode.put("id", step.id().value());
            stepNode.put("service", step.service().value());
            stepNode.put("operation", step.operation().raw());
            stepNode.put("timeoutSeconds", step.timeout().toSeconds());
            ArrayNode dependsOn = stepNode.putArray("dependsOn");
            step.dependsOn().forEach(dep -> dependsOn.add(dep.value()));
            ObjectNode retry = stepNode.putObject("retry");
            retry.put("maxAttempts", step.retry().maxAttempts());
            retry.put("backoff", step.retry().backoff().name());
            step.compensationOperation().ifPresent(op -> stepNode.put("compensation", op.raw()));
        }
        return node;
    }

    private WorkflowDefinition definitionFromJson(JsonNode node) {
        List<Step> steps = new ArrayList<>();
        for (JsonNode stepNode : node.path("steps")) {
            List<StepId> dependsOn = new ArrayList<>();
            stepNode.path("dependsOn").forEach(dep -> dependsOn.add(StepId.of(dep.asText())));
            JsonNode retryNode = stepNode.path("retry");
            RetryPolicy retry = new RetryPolicy(retryNode.path("maxAttempts").asInt(1),
                    RetryPolicy.BackoffStrategy.valueOf(retryNode.path("backoff").asText("FIXED")));
            JsonNode compensationNode = stepNode.path("compensation");
            StepOperation compensation = compensationNode.isMissingNode() || compensationNode.isNull()
                    ? null : StepOperation.parse(compensationNode.asText());

            steps.add(new Step(
                    StepId.of(stepNode.path("id").asText()),
                    ServiceId.of(stepNode.path("service").asText()),
                    StepOperation.parse(stepNode.path("operation").asText()),
                    dependsOn,
                    Duration.ofSeconds(stepNode.path("timeoutSeconds").asLong()),
                    retry,
                    compensation));
        }

        JsonNode policyNode = node.path("executionPolicy");
        List<StepId> approvalSteps = new ArrayList<>();
        policyNode.path("manualApprovalSteps").forEach(s -> approvalSteps.add(StepId.of(s.asText())));
        ExecutionPolicy executionPolicy = new ExecutionPolicy(
                Duration.ofSeconds(policyNode.path("globalTimeoutSeconds").asLong()),
                approvalSteps,
                policyNode.path("maxParallelSteps").asInt(1));

        return WorkflowDefinition.of(
                WorkflowId.of(node.path("id").asText()),
                steps,
                CompensationStrategy.valueOf(node.path("compensationStrategy").asText()),
                executionPolicy);
    }

    private ObjectNode outputToJson(Map<String, String> output) {
        ObjectNode node = mapper.createObjectNode();
        output.forEach(node::put);
        return node;
    }

    private Map<String, String> outputFromJson(JsonNode node) {
        Map<String, String> output = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> output.put(entry.getKey(), entry.getValue().asText()));
        return Map.copyOf(output);
    }

    private String writeJson(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new WorkflowStoreException("Serialisation JSON impossible : " + e.getMessage(), e);
        }
    }

    private JsonNode readJson(String json) throws IOException {
        return mapper.readTree(json);
    }
}
