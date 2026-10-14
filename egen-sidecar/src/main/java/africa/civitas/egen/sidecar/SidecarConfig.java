package africa.civitas.egen.sidecar;

import java.util.Map;

/**
 * Configuration du sidecar, lue dans l'environnement (voir docs/architecture/12,
 * "Sidecar de reference") : {@code EGEN_CONTROL_PLANE_URL} (obligatoire),
 * {@code EGEN_TOKEN} (optionnel), {@code EGEN_SIDECAR_PORT} (defaut 3500).
 */
public record SidecarConfig(String controlPlaneUrl, String token, int port) {

    public static final int DEFAULT_PORT = 3500;

    public static SidecarConfig fromEnvironment(Map<String, String> env) {
        String url = env.get("EGEN_CONTROL_PLANE_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("EGEN_CONTROL_PLANE_URL est obligatoire");
        }
        String token = env.get("EGEN_TOKEN");
        String rawPort = env.get("EGEN_SIDECAR_PORT");
        int port = DEFAULT_PORT;
        if (rawPort != null && !rawPort.isBlank()) {
            try {
                port = Integer.parseInt(rawPort.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("EGEN_SIDECAR_PORT invalide : \"" + rawPort + "\"");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("EGEN_SIDECAR_PORT hors plage : " + port);
            }
        }
        return new SidecarConfig(url.trim(), token == null || token.isBlank() ? null : token, port);
    }
}
