package africa.civitas.egen.application.usecase;

import java.util.Objects;

/**
 * Evenement a publier. {@code data} est le JSON brut, OPAQUE pour le Kernel. {@code correlationId} et
 * {@code causationId} sont optionnels : un identifiant de correlation est genere s'il est absent.
 */
public record PublishEventCommand(String type, String source, String correlationId, String causationId,
                                  String data) {

    public PublishEventCommand {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type est obligatoire");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source est obligatoire");
        }
        Objects.requireNonNull(data, "data est obligatoire");
    }
}
