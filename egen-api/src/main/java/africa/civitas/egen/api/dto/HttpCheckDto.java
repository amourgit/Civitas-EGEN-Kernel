package africa.civitas.egen.api.dto;

public class HttpCheckDto {
    public String endpoint;
    public String interval;
    public String timeout;
    public int failuresBeforeUnhealthy = 3;
}
