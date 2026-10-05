package africa.civitas.egen.bootstrap.wiring;

import africa.civitas.egen.adapter.postgresregistry.PostgresWorkflowStoreAdapter;
import africa.civitas.egen.application.observability.ObservedWorkflowStorePort;
import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.WorkflowStorePort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import javax.sql.DataSource;

/**
 * Seul point du Kernel qui sait que le WorkflowStorePort est implemente par
 * PostgreSQL, sur la MEME base que RegistryStorePort — voir
 * docs/architecture/11-moteur-de-workflow.md, "Durabilite de l'execution",
 * et RegistryAdapterBeans pour le meme raisonnement sur le partage du
 * {@link DataSource}. Decore par {@link ObservedWorkflowStorePort}.
 */
@ApplicationScoped
public class WorkflowStoreAdapterBeans {

    @Inject
    DataSource dataSource;

    @Inject
    ObservabilityPort observabilityPort;

    @Produces
    @ApplicationScoped
    public WorkflowStorePort workflowStorePort() {
        PostgresWorkflowStoreAdapter adapter = new PostgresWorkflowStoreAdapter(dataSource);
        return new ObservedWorkflowStorePort(adapter, observabilityPort, "postgres-workflow-store");
    }
}
