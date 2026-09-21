package africa.civitas.egen.api.dto;

public class ResolvedInstanceDto {
    public String instanceId;
    public String address;
    public int port;

    public ResolvedInstanceDto(String instanceId, String address, int port) {
        this.instanceId = instanceId;
        this.address = address;
        this.port = port;
    }
}
