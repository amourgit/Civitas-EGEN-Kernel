package africa.civitas.egen.application.port;

import java.util.Map;

/**
 * Retour neutre de {@link ServiceInvocationPort#invoke} — voir
 * docs/architecture/07-ports-et-adapters.md, "Service Invocation Port".
 * Un code {@code 4xx}/{@code 5xx} EST une reponse valide (jamais une
 * exception) : seule une erreur reseau (DNS, connexion refusee, timeout)
 * empeche la construction d'un {@code InvocationResult} et remonte en
 * exception depuis l'adapter.
 */
public record InvocationResult(int statusCode, Map<String, String> body) {

    public InvocationResult {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException("InvocationResult.statusCode hors plage HTTP valide : " + statusCode);
        }
        body = body == null ? Map.of() : Map.copyOf(body);
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }
}
