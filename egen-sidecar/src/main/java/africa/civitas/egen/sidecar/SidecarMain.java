package africa.civitas.egen.sidecar;

import africa.civitas.egen.sdk.EgenClient;

import java.io.IOException;

/** Point d'entree : {@code java -jar egen-sidecar.jar} (configuration par variables d'environnement). */
public final class SidecarMain {

    private SidecarMain() {
    }

    public static void main(String[] args) throws IOException {
        SidecarConfig config;
        try {
            config = SidecarConfig.fromEnvironment(System.getenv());
        } catch (IllegalArgumentException e) {
            System.err.println("egen-sidecar : " + e.getMessage());
            System.exit(2);
            return;
        }
        EgenClient.Builder builder = EgenClient.builder(config.controlPlaneUrl());
        if (config.token() != null) {
            builder.tokenSupplier(config::token);
        }
        SidecarServer server = new SidecarServer(builder.build(), "127.0.0.1", config.port());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("egen-sidecar : ecoute sur 127.0.0.1:" + server.port()
                + " (plan de controle : " + config.controlPlaneUrl() + ")");
    }
}
