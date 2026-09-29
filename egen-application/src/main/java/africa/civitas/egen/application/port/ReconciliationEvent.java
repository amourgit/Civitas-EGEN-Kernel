package africa.civitas.egen.application.port;

import africa.civitas.egen.domain.model.ServiceId;

import java.time.Instant;

/**
 * Evenement structure emis par le moteur de reconciliation — voir
 * docs/architecture/04-moteur-de-reconciliation.md, "operationId propage
 * dans les logs/traces de tous les appels aux ports", et
 * docs/architecture/15-observabilite.md ("les logs sont... toujours
 * enrichis de serviceId, generation, operationId, traceId").
 */
public record ReconciliationEvent(String operationId, ServiceId serviceId, long generation, String message,
                                   Instant timestamp) {

    public ReconciliationEvent {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("ReconciliationEvent.operationId ne peut pas etre vide");
        }
        if (serviceId == null) {
            throw new IllegalArgumentException("ReconciliationEvent.serviceId est obligatoire");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("ReconciliationEvent.message ne peut pas etre vide");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("ReconciliationEvent.timestamp est obligatoire");
        }
    }
}
