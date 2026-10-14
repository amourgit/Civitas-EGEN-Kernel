package africa.civitas.egen.sdk;

/** Une instance saine d'un service, telle que resolue par le Kernel. */
public record ServiceInstance(String instanceId, String address, int port) {
}
