package africa.civitas.egen.application.port;

import java.time.Instant;

/**
 * Detail neutre d'une instance en cours d'execution — ne contient aucun type
 * du moteur specialise sous-jacent (voir
 * docs/architecture/07-ports-et-adapters.md, "DeploymentPort.listAllocations").
 */
public record AllocationInfo(String allocationId, String status, Instant since) {

    public AllocationInfo {
        if (allocationId == null || allocationId.isBlank()) {
            throw new IllegalArgumentException("AllocationInfo.allocationId ne peut pas etre vide");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("AllocationInfo.status ne peut pas etre vide");
        }
    }
}
