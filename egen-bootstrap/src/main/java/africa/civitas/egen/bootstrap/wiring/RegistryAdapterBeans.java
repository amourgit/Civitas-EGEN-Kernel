package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.postgresregistry.PostgresRegistryAdapter;
import africa.civitas.egen.application.port.RegistryStorePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import javax.sql.DataSource;

/**
 * Seul point du Kernel qui sait que le RegistryStorePort est, depuis la
 * Phase 2, implemente par PostgreSQL (voir
 * docs/architecture/08-registry.md). Le {@link DataSource} injecte est
 * celui produit par l'extension Quarkus Agroal (configuree via
 * {@code quarkus.datasource.*}, voir application.properties) — le module
 * egen-adapter-postgres-registry lui-meme ne connait ni Quarkus ni Agroal,
 * seulement l'interface JDBC standard (garde-fou n6,
 * docs/architecture/02-principes-fondamentaux.md).
 */
@ApplicationScoped
public class RegistryAdapterBeans {

    @Inject
    DataSource dataSource;

    @Produces
    @ApplicationScoped
    public RegistryStorePort registryStorePort() {
        return new PostgresRegistryAdapter(dataSource);
    }
}
