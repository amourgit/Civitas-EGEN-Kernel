package africa.civitas.egen.kernel.moduleregistry.infrastructure;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ActivationRepository implements PanacheRepositoryBase<ActivationEntity, UUID> {

    public Optional<ActivationEntity> trouverActive(UUID celluleId, String moduleId) {
        return find("celluleId = ?1 and moduleId = ?2 and tracabilite.supprimeLe is null",
                celluleId, moduleId).firstResultOptional();
    }

    public boolean existeActive(UUID celluleId, String moduleId) {
        return trouverActive(celluleId, moduleId).isPresent();
    }

    public List<ActivationEntity> listerPourCellule(UUID celluleId) {
        return list("celluleId = ?1 order by tracabilite.creeLe desc", celluleId);
    }
}
