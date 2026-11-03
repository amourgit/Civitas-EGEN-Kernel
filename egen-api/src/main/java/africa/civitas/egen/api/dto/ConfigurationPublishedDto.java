package africa.civitas.egen.api.dto;

/** Reponse 202 de {@code PUT /api/v1/services/{id}/configuration} (docs 13, A3). */
public class ConfigurationPublishedDto {
    public String serviceId;
    public String environment;
    public long version;
    public String configurationUrl;

    public ConfigurationPublishedDto(String serviceId, String environment, long version, String configurationUrl) {
        this.serviceId = serviceId;
        this.environment = environment;
        this.version = version;
        this.configurationUrl = configurationUrl;
    }
}
