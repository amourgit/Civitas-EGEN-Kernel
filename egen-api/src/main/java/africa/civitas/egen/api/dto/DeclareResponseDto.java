package africa.civitas.egen.api.dto;

public class DeclareResponseDto {
    public String serviceId;
    public long generation;
    public String statusUrl;

    public DeclareResponseDto(String serviceId, long generation, String statusUrl) {
        this.serviceId = serviceId;
        this.generation = generation;
        this.statusUrl = statusUrl;
    }
}
