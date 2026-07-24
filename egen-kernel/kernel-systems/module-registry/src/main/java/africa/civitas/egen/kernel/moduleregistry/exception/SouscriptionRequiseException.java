package africa.civitas.egen.kernel.moduleregistry.exception;

/**
 * Leve quand une Activation est demandee pour une Organisation qui n'a aucune
 * Souscription active pour ce module — le deuxieme palier de la cascade (§B.11)
 * n'est jamais franchissable sans avoir d'abord franchi le premier.
 */
public class SouscriptionRequiseException extends RuntimeException {

    public SouscriptionRequiseException(String message) {
        super(message);
    }
}
