package africa.civitas.egen.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Corps de {@code POST /api/v1/events/publish} : un evenement CloudEvents (docs/architecture/07, 12, 13).
 * {@code data} est n'importe quelle valeur JSON, OPAQUE pour le Kernel. L'{@code id} et l'horodatage sont
 * attribues par le Kernel. Tout champ inconnu est refuse (400).
 */
public class PublishEventRequestDto {
    public String specversion;
    public String type;
    public String source;
    public JsonNode data;
    @JsonProperty("egen_correlation_id")
    public String correlationId;
    @JsonProperty("egen_causation_id")
    public String causationId;
}
