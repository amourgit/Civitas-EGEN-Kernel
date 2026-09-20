package africa.civitas.egen.application.port;

/**
 * Erreur remontee par un {@link DeploymentPort} — jamais une exception non
 * categorisee de son SDK sous-jacent (voir
 * docs/architecture/07-ports-et-adapters.md, "Pieges connus" : l'adapter doit
 * remonter une erreur exploitable par la boucle de reconciliation).
 */
public class DeploymentException extends RuntimeException {

    public DeploymentException(String message) {
        super(message);
    }

    public DeploymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
