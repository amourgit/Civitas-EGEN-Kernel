package africa.civitas.egen.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Bloc {@code configuration} du ServiceManifest — SANS valeurs (voir docs/architecture/06). */
public class ConfigurationDto {
    public List<KeyDto> required;
    public List<SecretRefDto> secretsRefs;

    public static class KeyDto {
        public String key;
        public String type;
        @JsonProperty("default")
        public String defaultValue;
    }

    public static class SecretRefDto {
        public String key;
        public String provider;
        public String path;
    }
}
