package africa.civitas.egen.api.mapper;

import java.io.IOException;

/**
 * Un document YAML refusé par {@link YamlMappers} : trop grand, vide, ou rejeté par l'analyseur. Une
 * {@link IOException}, comme toute erreur d'analyse de Jackson : les ressources REST la traitent déjà et répondent
 * {@code 400}.
 */
public final class YamlRejectedException extends IOException {

    public YamlRejectedException(String message) {
        super(message);
    }

    public YamlRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
