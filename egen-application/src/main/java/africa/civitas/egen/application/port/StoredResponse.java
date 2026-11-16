package africa.civitas.egen.application.port;

import java.util.List;
import java.util.Map;

/**
 * Reponse HTTP d'une mutation, gardee pour etre rejouee a l'identique quand la meme cle d'idempotence revient
 * (docs/architecture/13, « Idempotence »).
 *
 * @param status    code HTTP (toujours un 2xx : une erreur n'est jamais rejouee)
 * @param mediaType type du corps (par exemple {@code application/json}) ; peut etre {@code null} sans corps
 * @param body      corps deja serialise en texte ; {@code null} quand la reponse n'a pas de corps
 * @param headers   en-tetes a rejouer (sauf Content-Length, Date et Content-Type, recalcules au rejeu)
 */
public record StoredResponse(int status, String mediaType, String body, Map<String, List<String>> headers) {
    public StoredResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
