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
 *
 * <p><b>Isolation entre tests</b> : le conteneur PostgreSQL ({@link #POSTGRES})
 * est statique et partage entre toutes les methodes de la classe, pour ne pas
 * payer un demarrage de conteneur par test — mais {@code generation} et
 * {@code history} sont des compteurs persistes PAR {@link ServiceId} dans ce
 * meme conteneur partage. Si deux methodes utilisaient le meme ServiceId,
 * leurs ecritures s'accumuleraient l'une sur l'autre et le resultat
 * dependrait de l'ordre (non garanti) d'execution des tests JUnit — c'est
 * exactement ce qui provoquait l'echec observe en CI (generation attendue a
 * 1 ou 3, obtenue a 3 ou 6). Chaque methode utilise donc son propre
 * ServiceId, dedie et distinct : les tests restent independants les uns des
 * autres sans avoir besoin de reinitialiser le schema entre eux.</p>
 */
@Testcontainers
class PostgresRegistryAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private PostgresRegistryAdapter adapter() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return new PostgresRegistryAdapter((DataSource) dataSource);
    }

    private static ServiceManifest aManifest(ServiceId id) {
        return new ServiceManifest(id, ServiceVersion.parse("2.4.0"),
                new ServiceRuntime(RuntimeType.CONTAINER,
                        "registry.civitas.africa/news-service:2.4.0", "python"),
                new DeploymentSpec("nomad", "registry.civitas.africa/news-service:2.4.0",
                        "500m", "512Mi", new ReplicaRange(2, 6)),
                new HealthSpec("/health", Duration.ofSeconds(10), Duration.ofSeconds(2), 3),
                LifecyclePolicy.defaultPolicy());
    }

    @Test
    void savingRoundTripsTheDesiredStateThroughRealPostgres() {
        ServiceId id = ServiceId.of("it-fixture-round-trip");
        PostgresRegistryAdapter adapter = adapter();

        DesiredState saved = adapter.save(new DesiredState(aManifest(id), 0L, TargetEnvironment.of("test")));
        assertEquals(1L, saved.generation());

        DesiredState reloaded = adapter.findById(id).orElseThrow();
        assertEquals(id, reloaded.serviceId());
        assertEquals(aManifest(id).version(), reloaded.manifest().version());
        assertEquals(aManifest(id).health().httpEndpoint(), reloaded.manifest().health().httpEndpoint());
        assertEquals(1L, reloaded.generation());
    }

    @Test
    void generationIncrementsOnEverySaveAndHistoryKeepsEachVersion() {
        ServiceId id = ServiceId.of("it-fixture-generation-history");
        PostgresRegistryAdapter adapter = adapter();

        adapter.save(new DesiredState(aManifest(id), 0L, TargetEnvironment.of("test")));
        adapter.save(new DesiredState(aManifest(id), 0L, TargetEnvironment.of("test")));
        DesiredState third = adapter.save(new DesiredState(aManifest(id), 0L, TargetEnvironment.of("test")));

        assertEquals(3L, third.generation());
        List<DesiredState> history = adapter.history(id);
        assertEquals(3, history.size());
        assertEquals(1L, history.get(0).generation());
        assertEquals(3L, history.get(2).generation());
    }

    @Test
    void statusRoundTripsWithConditions() {
        ServiceId id = ServiceId.of("it-fixture-status");
        PostgresRegistryAdapter adapter = adapter();
        adapter.save(new DesiredState(aManifest(id), 0L, TargetEnvironment.of("test")));

        adapter.saveStatus(id, new ServiceStatus(Phase.RUNNING, List.of(), 1L));
        ServiceStatus status = adapter.findStatus(id).orElseThrow();

        assertEquals(Phase.RUNNING, status.phase());
        assertEquals(1L, status.observedGeneration());
    }

    @Test
    void findAllIdsListsEveryDeclaredService() {
        ServiceId id = ServiceId.of("it-fixture-find-all");
        PostgresRegistryAdapter adapter = adapter();
        adapter.save(new DesiredState(aManifest(id), 0L, TargetEnvironment.of("test")));

        assertTrue(adapter.findAllIds().contains(id));
    }
}
