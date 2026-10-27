package africa.civitas.egen.sdk;

/**
 * Options d'une publication — toutes optionnelles (docs/architecture/12, "Resilience de la publication
 * d'evenements"). {@code source} prend le pas sur celle du client ; {@code correlationId}, {@code traceparent}
 * et {@code idempotencyKey} sont generes par le SDK s'ils sont absents et propages tels quels sinon. Une
 * {@code idempotencyKey} fournie par l'appelant rend la publication sure meme apres une reprise du processus.
 */
public record PublishOptions(String source, String correlationId, String causationId, String traceparent,
                             String idempotencyKey) {

    public static PublishOptions none() {
        return new PublishOptions(null, null, null, null, null);
    }

    public PublishOptions withSource(String value) {
        return new PublishOptions(value, correlationId, causationId, traceparent, idempotencyKey);
    }

    public PublishOptions withCorrelationId(String value) {
        return new PublishOptions(source, value, causationId, traceparent, idempotencyKey);
    }

    public PublishOptions withCausationId(String value) {
        return new PublishOptions(source, correlationId, value, traceparent, idempotencyKey);
    }

    public PublishOptions withTraceparent(String value) {
        return new PublishOptions(source, correlationId, causationId, value, idempotencyKey);
    }

    public PublishOptions withIdempotencyKey(String value) {
        return new PublishOptions(source, correlationId, causationId, traceparent, value);
    }
}
