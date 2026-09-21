package africa.civitas.egen.adapter.postgresregistry;

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
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de niveau 3 (voir docs/architecture/17-strategie-de-tests.md) : demarre
 * un vrai PostgreSQL via Testcontainers et verifie le mapping de
 * {@link PostgresRegistryAdapter} contre l'API JDBC reelle, y compris le
 * verrouillage optimiste sur {@code generation} (voir
 * docs/architecture/08-registry.md).
 */
@Testcontainers
class PostgresRegistryAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ServiceId ID = ServiceId.of("it-fixture-service");

    private PostgresRegistryAdapter adapter() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return new PostgresRegistryAdapter((DataSource) dataSource);
    }

    private static ServiceManifest aManifest() {
        return new ServiceManifest(ID, ServiceVersion.parse("2.4.0"),
                new ServiceRuntime(RuntimeType.CONTAINER,
                        "registry.civitas.africa/news-service:2.4.0", "python"),
                new DeploymentSpec("nomad", "registry.civitas.africa/news-service:2.4.0",
                        "500m", "512Mi", new ReplicaRange(2, 6)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                LifecyclePolicy.defaultPolicy());
    }

    @Test
    void savingRoundTripsTheDesiredStateThroughRealPostgres() {
        PostgresRegistryAdapter adapter = adapter();

        DesiredState saved = adapter.save(new DesiredState(aManifest(), 0L, TargetEnvironment.of("test")));
        assertEquals(1L, saved.generation());

        DesiredState reloaded = adapter.findById(ID).orElseThrow();
        assertEquals(ID, reloaded.serviceId());
        assertEquals(aManifest().version(), reloaded.manifest().version());
        assertEquals(aManifest().health().httpEndpoint(), reloaded.manifest().health().httpEndpoint());
        assertEquals(1L, reloaded.generation());
    }

    @Test
    void generationIncrementsOnEverySaveAndHistoryKeepsEachVersion() {
        PostgresRegistryAdapter adapter = adapter();

        adapter.save(new DesiredState(aManifest(), 0L, TargetEnvironment.of("test")));
        adapter.save(new DesiredState(aManifest(), 0L, TargetEnvironment.of("test")));
        DesiredState third = adapter.save(new DesiredState(aManifest(), 0L, TargetEnvironment.of("test")));

        assertEquals(3L, third.generation());
        List<DesiredState> history = adapter.history(ID);
        assertEquals(3, history.size());
        assertEquals(1L, history.get(0).generation());
        assertEquals(3L, history.get(2).generation());
    }

    @Test
    void statusRoundTripsWithConditions() {
        PostgresRegistryAdapter adapter = adapter();
        adapter.save(new DesiredState(aManifest(), 0L, TargetEnvironment.of("test")));

        adapter.saveStatus(ID, new ServiceStatus(Phase.RUNNING, List.of(), 1L));
        ServiceStatus status = adapter.findStatus(ID).orElseThrow();

        assertEquals(Phase.RUNNING, status.phase());
        assertEquals(1L, status.observedGeneration());
    }

    @Test
    void findAllIdsListsEveryDeclaredService() {
        PostgresRegistryAdapter adapter = adapter();
        adapter.save(new DesiredState(aManifest(), 0L, TargetEnvironment.of("test")));

        assertTrue(adapter.findAllIds().contains(ID));
    }
}
