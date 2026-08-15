package africa.civitas.egen.kernel.moduleregistry.infrastructure;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ActivationRepository implements PanacheRepositoryBase<ActivationEntity, UUID> {

    public Optional<ActivationEntity> trouverActive(UUID contexteId, String moduleId) {
        return find("contexteId = ?1 and moduleId = ?2 and tracabilite.supprimeLe is null",
                contexteId, moduleId).firstResultOptional();
    }

    public boolean existeActive(UUID contexteId, String moduleId) {
        return trouverActive(contexteId, moduleId).isPresent();
    }

    public List<ActivationEntity> listerPourContexte(UUID contexteId) {
        return list("contexteId = ?1 order by tracabilite.creeLe desc", contexteId);
    }
}
