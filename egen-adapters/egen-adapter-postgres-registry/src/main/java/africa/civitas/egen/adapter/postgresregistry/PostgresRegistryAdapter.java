package africa.civitas.egen.adapter.postgresregistry;

import africa.civitas.egen.application.port.RegistryException;
import africa.civitas.egen.application.port.RegistryStorePort;
import africa.civitas.egen.domain.lifecycle.Condition;
import africa.civitas.egen.domain.lifecycle.ConditionStatus;
import africa.civitas.egen.domain.lifecycle.Phase;
import africa.civitas.egen.domain.lifecycle.ServiceStatus;
import africa.civitas.egen.domain.model.DeploymentSpec;
import africa.civitas.egen.domain.model.DesiredState;
import africa.civitas.egen.domain.model.HealthSpec;
import africa.civitas.egen.domain.model.LifecyclePolicy;
import africa.civitas.egen.domain.model.ReplicaRange;
import africa.civitas.egen.domain.model.RuntimeType;
import africa.civitas.egen.domain.model.ServiceId;
import africa.civitas.egen.domain.model.ServiceManifest;
import africa.civitas.egen.domain.model.ServiceRuntime;
import africa.civitas.egen.domain.model.ServiceVersion;
import africa.civitas.egen.domain.model.TargetEnvironment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementation du {@link RegistryStorePort} en PostgreSQL — voir
 * docs/architecture/08-registry.md. Persistance transactionnelle du
 * {@link DesiredState} avec verrouillage OPTIMISTE sur {@code generation}
 * (lecture sans verrou, ecriture conditionnee par la valeur lue — voir
 * {@link #save}) : deux ecritures concurrentes sur le meme service ne
 * peuvent jamais silencieusement s'ecraser l'une l'autre, l'une des deux
 * doit rejouer.
 *
 * <p>N'utilise que l'API JDBC standard — voir la description du module dans
 * son pom.xml. Cree son propre schema au premier usage
 * ({@code CREATE TABLE IF NOT EXISTS}), pour rester autonome sans outil de
 * migration supplementaire a ce stade.</p>
 */
public final class PostgresRegistryAdapter implements RegistryStorePort {

    private static final int MAX_OPTIMISTIC_RETRIES = 5;

    private final DataSource dataSource;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public PostgresRegistryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
        createSchemaIfAbsent();
    }

    // ------------------------------------------------------------------
    // RegistryStorePort
    // ------------------------------------------------------------------

    @Override
    public DesiredState save(DesiredState desiredState) {
        ServiceId id = desiredState.serviceId();
        String manifestJson = writeJson(manifestToJson(desiredState.manifest()));
        String environment = desiredState.targetEnvironment().name();

        for (int attempt = 1; attempt <= MAX_OPTIMISTIC_RETRIES; attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                long currentGeneration = readCurrentGeneration(connection, id);
                long newGeneration = currentGeneration + 1;

                if (upsertDesiredState(connection, id, newGeneration, manifestJson, environment,
                        currentGeneration)) {
                    insertHistory(connection, id, newGeneration, manifestJson, environment);
                    connection.commit();
                    return new DesiredState(desiredState.manifest(), newGeneration,
                            desiredState.targetEnvironment());
                }
                connection.rollback(); // course perdue (verrouillage optimiste) : on reessaie
            } catch (SQLException e) {
                throw new RegistryException("Echec de sauvegarde du DesiredState pour " + id, e);
            }
        }
        throw new RegistryException(
                "Verrouillage optimiste : trop d'ecritures concurrentes pour " + id
                        + " (" + MAX_OPTIMISTIC_RETRIES + " tentatives)");
    }

    @Override
    public Optional<DesiredState> findById(ServiceId id) {
        String sql = "SELECT generation, manifest_json, target_environment "
                + "FROM egen_desired_state WHERE service_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(toDesiredState(resultSet));
            }
        } catch (SQLException | IOException e) {
            throw new RegistryException("Echec de lecture du DesiredState pour " + id, e);
        }
    }

    @Override
    public void saveStatus(ServiceId id, ServiceStatus status) {
        String sql = "INSERT INTO egen_service_status "
                + "(service_id, phase, conditions_json, observed_generation, updated_at) "
                + "VALUES (?, ?, ?, ?, now()) "
                + "ON CONFLICT (service_id) DO UPDATE SET "
                + "phase = EXCLUDED.phase, conditions_json = EXCLUDED.conditions_json, "
                + "observed_generation = EXCLUDED.observed_generation, updated_at = now()";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            statement.setString(2, status.phase().name());
            statement.setString(3, writeJson(conditionsToJson(status.conditions())));
            statement.setLong(4, status.observedGeneration());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RegistryException("Echec de sauvegarde du ServiceStatus pour " + id, e);
        }
    }

    @Override
    public Optional<ServiceStatus> findStatus(ServiceId id) {
        String sql = "SELECT phase, conditions_json, observed_generation "
                + "FROM egen_service_status WHERE service_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Phase phase = Phase.valueOf(resultSet.getString("phase"));
                List<Condition> conditions = conditionsFromJson(readJson(resultSet.getString("conditions_json")));
                long observedGeneration = resultSet.getLong("observed_generation");
                return Optional.of(new ServiceStatus(phase, conditions, observedGeneration));
            }
        } catch (SQLException | IOException e) {
            throw new RegistryException("Echec de lecture du ServiceStatus pour " + id, e);
        }
    }

    @Override
    public List<ServiceId> findAllIds() {
        String sql = "SELECT service_id FROM egen_desired_state";
        List<ServiceId> ids = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                ids.add(ServiceId.of(resultSet.getString("service_id")));
            }
            return List.copyOf(ids);
        } catch (SQLException e) {
            throw new RegistryException("Echec de lecture de la liste des services", e);
        }
    }

    @Override
    public List<DesiredState> history(ServiceId id) {
        String sql = "SELECT generation, manifest_json, target_environment "
                + "FROM egen_desired_state_history WHERE service_id = ? ORDER BY generation ASC";
        List<DesiredState> history = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    history.add(toDesiredState(resultSet));
                }
            }
            return List.copyOf(history);
        } catch (SQLException | IOException e) {
            throw new RegistryException("Echec de lecture de l'historique pour " + id, e);
        }
    }

    // ------------------------------------------------------------------
    // Verrouillage optimiste — details JDBC
    // ------------------------------------------------------------------

    private long readCurrentGeneration(Connection connection, ServiceId id) throws SQLException {
        String sql = "SELECT generation FROM egen_desired_state WHERE service_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        }
    }

    /**
     * INSERT..ON CONFLICT..DO UPDATE..WHERE generation = {@code expectedGeneration} :
     * la clause WHERE est LE verrou optimiste — si une autre transaction a
     * deja fait progresser {@code generation} entre notre lecture et cette
     * ecriture, la mise a jour ne touche aucune ligne et {@code false} est
     * retourne, sans jamais ecraser silencieusement l'ecriture concurrente.
     */
    private boolean upsertDesiredState(Connection connection, ServiceId id, long newGeneration,
                                        String manifestJson, String environment,
                                        long expectedGeneration) throws SQLException {
        String sql = "INSERT INTO egen_desired_state "
                + "(service_id, generation, manifest_json, target_environment, updated_at) "
                + "VALUES (?, ?, ?, ?, now()) "
                + "ON CONFLICT (service_id) DO UPDATE SET "
                + "generation = EXCLUDED.generation, manifest_json = EXCLUDED.manifest_json, "
                + "target_environment = EXCLUDED.target_environment, updated_at = now() "
                + "WHERE egen_desired_state.generation = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            statement.setLong(2, newGeneration);
            statement.setString(3, manifestJson);
            statement.setString(4, environment);
            statement.setLong(5, expectedGeneration);
            return statement.executeUpdate() == 1;
        }
    }

    private void insertHistory(Connection connection, ServiceId id, long generation,
                                String manifestJson, String environment) throws SQLException {
        String sql = "INSERT INTO egen_desired_state_history "
                + "(service_id, generation, manifest_json, target_environment, created_at) "
                + "VALUES (?, ?, ?, ?, now())";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.value());
            statement.setLong(2, generation);
            statement.setString(3, manifestJson);
            statement.setString(4, environment);
            statement.executeUpdate();
        }
    }

    private DesiredState toDesiredState(ResultSet resultSet) throws SQLException, IOException {
        long generation = resultSet.getLong("generation");
        ServiceManifest manifest = manifestFromJson(readJson(resultSet.getString("manifest_json")));
        TargetEnvironment environment = TargetEnvironment.of(resultSet.getString("target_environment"));
        return new DesiredState(manifest, generation, environment);
    }

    // ------------------------------------------------------------------
    // Schema
    // ------------------------------------------------------------------

    private void createSchemaIfAbsent() {
        String[] ddl = {
                "CREATE TABLE IF NOT EXISTS egen_desired_state ("
                        + "service_id VARCHAR(255) PRIMARY KEY, "
                        + "generation BIGINT NOT NULL, "
                        + "manifest_json TEXT NOT NULL, "
                        + "target_environment VARCHAR(255) NOT NULL, "
                        + "updated_at TIMESTAMPTZ NOT NULL DEFAULT now())",
                "CREATE TABLE IF NOT EXISTS egen_desired_state_history ("
                        + "service_id VARCHAR(255) NOT NULL, "
                        + "generation BIGINT NOT NULL, "
                        + "manifest_json TEXT NOT NULL, "
                        + "target_environment VARCHAR(255) NOT NULL, "
                        + "created_at TIMESTAMPTZ NOT NULL DEFAULT now(), "
                        + "PRIMARY KEY (service_id, generation))",
                "CREATE TABLE IF NOT EXISTS egen_service_status ("
                        + "service_id VARCHAR(255) PRIMARY KEY, "
                        + "phase VARCHAR(64) NOT NULL, "
                        + "conditions_json TEXT NOT NULL, "
                        + "observed_generation BIGINT NOT NULL, "
                        + "updated_at TIMESTAMPTZ NOT NULL DEFAULT now())",
        };
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : ddl) {
                statement.executeUpdate(sql);
            }
        } catch (SQLException e) {
            throw new RegistryException("Echec de creation du schema du Registry", e);
        }
    }

    // ------------------------------------------------------------------
    // JSON — traduction manuelle et explicite du ServiceManifest (voir
    // NomadDeploymentAdapter/ConsulDiscoveryAdapter pour le meme choix :
    // controle total sur le mapping plutot que la (de)serialisation
    // automatique de records a invariants valides).
    // ------------------------------------------------------------------

    private ObjectNode manifestToJson(ServiceManifest manifest) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", manifest.id().value());
        node.put("version", manifest.version().toString());

        ObjectNode runtime = node.putObject("runtime");
        runtime.put("type", manifest.runtime().type().name());
        runtime.put("artifact", manifest.runtime().artifact());
        runtime.put("language", manifest.runtime().language());

        ObjectNode deployment = node.putObject("deployment");
        deployment.put("adapter", manifest.deployment().adapter());
        deployment.put("image", manifest.deployment().image());
        deployment.put("cpu", manifest.deployment().cpu());
        deployment.put("memory", manifest.deployment().memory());
        deployment.put("replicasMin", manifest.deployment().replicas().min());
        deployment.put("replicasMax", manifest.deployment().replicas().max());

        ObjectNode health = node.putObject("health");
        health.put("httpEndpoint", manifest.health().httpEndpoint());
        health.put("intervalSeconds", manifest.health().interval().toSeconds());
        health.put("timeoutSeconds", manifest.health().timeout().toSeconds());
        health.put("failuresBeforeUnhealthy", manifest.health().failuresBeforeUnhealthy());

        ObjectNode lifecycle = node.putObject("lifecycle");
        lifecycle.put("shutdownGracePeriodSeconds", manifest.lifecycle().shutdownGracePeriod().toSeconds());

        return node;
    }

    private ServiceManifest manifestFromJson(JsonNode node) {
        JsonNode runtime = node.path("runtime");
        JsonNode deployment = node.path("deployment");
        JsonNode health = node.path("health");
        JsonNode lifecycle = node.path("lifecycle");

        return new ServiceManifest(
                ServiceId.of(node.path("id").asText()),
                ServiceVersion.parse(node.path("version").asText()),
                new ServiceRuntime(
                        RuntimeType.valueOf(runtime.path("type").asText()),
                        runtime.path("artifact").asText(),
                        runtime.path("language").asText(null)),
                new DeploymentSpec(
                        deployment.path("adapter").asText(),
                        deployment.path("image").asText(),
                        deployment.path("cpu").asText(),
                        deployment.path("memory").asText(),
                        new ReplicaRange(deployment.path("replicasMin").asInt(),
                                deployment.path("replicasMax").asInt())),
                new HealthSpec(
                        health.path("httpEndpoint").asText(),
                        Duration.ofSeconds(health.path("intervalSeconds").asLong()),
                        Duration.ofSeconds(health.path("timeoutSeconds").asLong()),
                        health.path("failuresBeforeUnhealthy").asInt()),
                new LifecyclePolicy(Duration.ofSeconds(lifecycle.path("shutdownGracePeriodSeconds").asLong())));
    }

    private ArrayNode conditionsToJson(List<Condition> conditions) {
        ArrayNode array = mapper.createArrayNode();
        for (Condition condition : conditions) {
            ObjectNode node = array.addObject();
            node.put("type", condition.type());
            node.put("status", condition.status().name());
            node.put("reason", condition.reason());
            node.put("message", condition.message());
            node.put("lastTransitionTime", condition.lastTransitionTime().toString());
        }
        return array;
    }

    private List<Condition> conditionsFromJson(JsonNode array) {
        List<Condition> conditions = new ArrayList<>();
        for (JsonNode node : array) {
            conditions.add(new Condition(
                    node.path("type").asText(),
                    ConditionStatus.valueOf(node.path("status").asText()),
                    node.path("reason").asText(),
                    node.path("message").asText(),
                    Instant.parse(node.path("lastTransitionTime").asText())));
        }
        return List.copyOf(conditions);
    }

    private String writeJson(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new RegistryException("Serialisation JSON impossible : " + e.getMessage(), e);
        }
    }

    private JsonNode readJson(String json) throws IOException {
        return mapper.readTree(json);
    }
}
