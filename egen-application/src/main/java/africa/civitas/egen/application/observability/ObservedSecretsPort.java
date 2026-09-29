package africa.civitas.egen.application.observability;

import africa.civitas.egen.application.port.ObservabilityPort;
import africa.civitas.egen.application.port.SecretRef;
import africa.civitas.egen.application.port.SecretReference;
import africa.civitas.egen.application.port.SecretsPort;

/**
 * Decore un {@link SecretsPort} concret des metriques generiques d'appel
 * d'adapter — voir {@link ObservedDeploymentPort}. Ne journalise et ne
 * transporte jamais la valeur d'un secret (voir
 * docs/architecture/14-securite.md) : {@link AdapterCallInstrumentation}
 * ne mesure qu'une duree et un succes/echec, jamais le contenu de
 * {@link SecretRef}.
 */
public final class ObservedSecretsPort implements SecretsPort {

    private final SecretsPort delegate;
    private final AdapterCallInstrumentation instrumentation;

    public ObservedSecretsPort(SecretsPort delegate, ObservabilityPort observabilityPort, String adapterName) {
        this.delegate = delegate;
        this.instrumentation = new AdapterCallInstrumentation(observabilityPort, adapterName);
    }

    @Override
    public SecretRef resolve(SecretReference reference) {
        return instrumentation.call("secrets.resolve", () -> delegate.resolve(reference));
    }

    @Override
    public void rotate(SecretReference reference) {
        instrumentation.run("secrets.rotate", () -> delegate.rotate(reference));
    }
}
