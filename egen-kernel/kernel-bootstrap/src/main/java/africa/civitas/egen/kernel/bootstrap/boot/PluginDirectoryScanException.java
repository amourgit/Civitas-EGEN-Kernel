package africa.civitas.egen.kernel.bootstrap.boot;

/** Levee quand le repertoire de plugins ne peut pas etre parcouru (droits, E/S...). */
public class PluginDirectoryScanException extends RuntimeException {

    public PluginDirectoryScanException(String message, Throwable cause) {
        super(message, cause);
    }
}
