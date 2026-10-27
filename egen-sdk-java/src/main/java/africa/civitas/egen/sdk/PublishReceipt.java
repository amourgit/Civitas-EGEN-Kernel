package africa.civitas.egen.sdk;

/** Accuse du Kernel apres {@code 202 Accepted} sur {@code POST /api/v1/events/publish} (docs/architecture/13). */
public record PublishReceipt(String id, String type, String correlationId) {
}
