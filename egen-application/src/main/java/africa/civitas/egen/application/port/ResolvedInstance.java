package africa.civitas.egen.application.port;

public record ResolvedInstance(String instanceId, String address, int port) {

    public ResolvedInstance {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalArgumentException("ResolvedInstance.instanceId ne peut pas etre vide");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("ResolvedInstance.address ne peut pas etre vide");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("ResolvedInstance.port doit etre positif");
        }
    }
}
