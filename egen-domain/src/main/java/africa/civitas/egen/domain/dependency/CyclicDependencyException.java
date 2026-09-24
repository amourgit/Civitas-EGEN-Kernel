package africa.civitas.egen.domain.dependency;

import africa.civitas.egen.domain.model.ServiceId;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Un cycle de dependance est TOUJOURS une erreur de conception metier,
 * jamais un cas a supporter techniquement (voir
 * docs/architecture/10-gestion-des-dependances.md, "Detection de cycle").
 * Rejete des l'etape Declare, avec le chemin complet du cycle.
 */
public final class CyclicDependencyException extends IllegalArgumentException {

    public CyclicDependencyException(List<ServiceId> cyclePath) {
        super("Cycle de dependance detecte : " + cyclePath.stream()
                .map(ServiceId::value)
                .collect(Collectors.joining(" -> ")));
    }
}
