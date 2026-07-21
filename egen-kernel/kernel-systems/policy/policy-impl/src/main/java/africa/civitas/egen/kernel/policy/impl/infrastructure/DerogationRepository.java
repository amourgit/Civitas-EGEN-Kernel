package africa.civitas.egen.kernel.policy.impl.infrastructure;

import africa.civitas.egen.kernel.policy.impl.domain.DerogationEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DerogationRepository implements PanacheRepositoryBase<DerogationEntity, UUID> {

    public List<DerogationEntity> listByPolitique(UUID politiqueId) {
        return list("politiqueId", politiqueId);
    }

    /** Recherche une Derogation precise pour une Politique et une Cellule donnees. */
    public Optional<DerogationEntity> findByPolitiqueAndCellule(UUID politiqueId, UUID celluleId) {
        return find("politiqueId = ?1 and celluleDerogatoireId = ?2", politiqueId, celluleId)
                .firstResultOptional();
    }
}
