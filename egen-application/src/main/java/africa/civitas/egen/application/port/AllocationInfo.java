package africa.civitas.egen.application.port;

import java.time.Instant;

/**
 * Detail neutre d'une instance en cours d'execution — ne contient aucun type
 * du moteur specialise sous-jacent (voir
 * docs/architecture/07-ports-et-adapters.md, "DeploymentPort.listAllocations").
 * {@code address}/{@code port} restent vides/nuls quand l'adapter ne peut
 * pas les determiner (ex. allocation pas encore planifiee) — l'appelant
 * (la boucle de reconciliation, pour l'enregistrement Discovery) doit gerer
 * cette absence sans lever d'exception.
 */
public record AllocationInfo(String allocationId, String status, Instant since, String address,
                              int port) {

    public AllocationInfo {
        if (allocationId == null || allocationId.isBlank()) {
            throw new IllegalArgumentException("AllocationInfo.allocationId ne peut pas etre vide");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("AllocationInfo.status ne peut pas etre vide");
        }
    }

    public boolean hasNetworkInfo() {
        return address != null && !address.isBlank() && port > 0;
    }
}
