package africa.civitas.egen.kernel.pluginengine.loader;

/** Levee quand le chargement physique d'un plugin echoue (voir {@link PluginLoader#charger}). */
public class PluginLoadException extends RuntimeException {

    public PluginLoadException(String message) {
        super(message);
    }

    public PluginLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
