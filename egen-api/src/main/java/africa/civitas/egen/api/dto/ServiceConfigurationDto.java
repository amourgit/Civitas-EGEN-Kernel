package africa.civitas.egen.api.dto;

import java.util.Map;

/** Reponse de {@code GET /api/v1/services/{id}/configuration} (docs 13, A3) : jamais de secret. */
public class ServiceConfigurationDto {
    public String serviceId;
    public String environment;
    public long version;
    public Map<String, String> values;

    public ServiceConfigurationDto(String serviceId, String environment, long version, Map<String, String> values) {
        this.serviceId = serviceId;
        this.environment = environment;
        this.version = version;
        this.values = values;
    }
}
