package africa.civitas.egen.sdk;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Reponse d'un service cible, renvoyee telle quelle (statut, corps, en-tetes). */
public record EgenResponse(int status, byte[] body, Map<String, java.util.List<String>> headers) {

    public EgenResponse {
        body = body == null ? new byte[0] : body.clone();
        headers = Map.copyOf(headers);
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public boolean isSuccess() {
        return status >= 200 && status < 300;
    }
}
