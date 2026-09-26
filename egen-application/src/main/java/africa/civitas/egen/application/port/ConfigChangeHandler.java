package africa.civitas.egen.application.port;

@FunctionalInterface
public interface ConfigChangeHandler {
    void onChange(ResolvedConfig newConfig);
}
