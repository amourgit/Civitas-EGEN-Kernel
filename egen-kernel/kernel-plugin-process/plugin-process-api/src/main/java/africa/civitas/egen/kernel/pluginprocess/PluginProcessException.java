package africa.civitas.egen.kernel.pluginprocess;

/**
 * Levee pour tout echec propre a l'isolation par processus — handshake malforme ou
 * absent, processus qui ne demarre jamais, appel distant qui echoue au niveau
 * transport (a distinguer d'une exception legitimement levee PAR l'extension
 * distante elle-meme, qui remonte via {@link ResultatAppelExtension.Echec} plutot
 * que par cette exception : ce n'est pas une anomalie du mecanisme, c'est le
 * comportement normal de l'extension).
 */
public class PluginProcessException extends RuntimeException {

    public PluginProcessException(String message) {
        super(message);
    }

    public PluginProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
