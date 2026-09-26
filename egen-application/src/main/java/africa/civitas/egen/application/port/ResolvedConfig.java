package africa.civitas.egen.application.port;

import java.util.Map;

public record ResolvedConfig(Map<String, String> values, ConfigVersion version) {

    public ResolvedConfig {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static ResolvedConfig empty() {
        return new ResolvedConfig(Map.of(), new ConfigVersion(0));
    }
}
