package africa.civitas.egen.adapter.kubernetes;

import java.net.URI;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Parametres de connexion du {@link KubernetesDeploymentAdapter} — voir
 * docs/architecture/07-ports-et-adapters.md, « Kubernetes Adapter », tableau
 * « Configuration ».
 *
 * @param address       URL de l'API Kubernetes (ex. {@code https://kubernetes.default.svc})
 * @param namespace     namespace des Deployments geres (jamais « default » code en dur)
 * @param token         jeton porteur d'un compte de service ; {@code null} = aucun en-tete d'authentification
 * @param caCertPath    fichier PEM de l'autorite de certification de l'API ; {@code null} = magasin de
 *                      confiance du JDK. La verification TLS n'est jamais desactivable.
 * @param containerPort port que le conteneur ecoute (voir « Reseau »)
 */
public record KubernetesConfig(URI address, String namespace, String token, Path caCertPath,
                               int containerPort) {

    /** Etiquette DNS-1123 : minuscules, chiffres et tirets, 63 caracteres au plus. */
    static final Pattern DNS_LABEL = Pattern.compile("^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$");

    public KubernetesConfig {
        if (address == null) {
            throw new IllegalArgumentException("KubernetesConfig.address est obligatoire");
        }
        if (namespace == null || !DNS_LABEL.matcher(namespace).matches()) {
            throw new IllegalArgumentException("KubernetesConfig.namespace invalide : \"" + namespace
                    + "\" — attendu une etiquette DNS-1123 (minuscules, chiffres, tirets)");
        }
        if (containerPort < 1 || containerPort > 65535) {
            throw new IllegalArgumentException(
                    "KubernetesConfig.containerPort doit etre entre 1 et 65535 : " + containerPort);
        }
    }

    // Le jeton ne doit jamais apparaitre dans un journal : toString() le masque.
    @Override
    public String toString() {
        return "KubernetesConfig[address=" + address + ", namespace=" + namespace
                + ", token=" + (token == null || token.isBlank() ? "absent" : "***")
                + ", caCertPath=" + caCertPath + ", containerPort=" + containerPort + "]";
    }
}
