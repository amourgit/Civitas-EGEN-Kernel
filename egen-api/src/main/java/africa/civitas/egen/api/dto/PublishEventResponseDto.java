package africa.civitas.egen.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Reponse {@code 202} de {@code POST /api/v1/events/publish}. */
public record PublishEventResponseDto(String id, String type,
                                      @JsonProperty("egen_correlation_id") String correlationId) { }
