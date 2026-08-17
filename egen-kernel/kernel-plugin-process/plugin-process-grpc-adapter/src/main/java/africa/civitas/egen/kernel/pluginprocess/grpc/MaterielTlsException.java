package africa.civitas.egen.kernel.pluginprocess.grpc;

/** Levee pour tout echec de generation ou de manipulation du materiel TLS ephemere. */
public class MaterielTlsException extends RuntimeException {

    public MaterielTlsException(String message) {
        super(message);
    }

    public MaterielTlsException(String message, Throwable cause) {
        super(message, cause);
    }
}
