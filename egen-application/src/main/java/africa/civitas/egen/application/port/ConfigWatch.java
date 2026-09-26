package africa.civitas.egen.application.port;

/** Reference neutre vers une souscription de configuration active — voir {@link ConfigurationPort#watch}. */
public record ConfigWatch(String nativeHandle) {

    public ConfigWatch {
        if (nativeHandle == null || nativeHandle.isBlank()) {
            throw new IllegalArgumentException("ConfigWatch.nativeHandle ne peut pas etre vide");
        }
    }
}
