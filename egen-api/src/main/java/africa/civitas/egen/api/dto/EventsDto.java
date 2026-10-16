package africa.civitas.egen.api.dto;

import java.util.List;

/** Bloc {@code events} du ServiceManifest (voir docs/architecture/06-service-manifest.md). */
public class EventsDto {
    public List<PublishedEventDto> publishes;
    public List<ConsumedEventDto> consumes;

    public static class PublishedEventDto {
        public String type;
        public String schemaRef;
    }

    public static class ConsumedEventDto {
        public String type;
        public String subscriptionGroup;
        public DeadLetterDto deadLetter;
    }

    public static class DeadLetterDto {
        public Integer maxRetries;
        public String strategy;
    }
}
