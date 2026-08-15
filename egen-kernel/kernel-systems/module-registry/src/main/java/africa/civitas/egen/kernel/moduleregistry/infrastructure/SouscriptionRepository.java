package africa.civitas.egen.kernel.moduleregistry.infrastructure;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SouscriptionRepository implements PanacheRepositoryBase<SouscriptionEntity, UUID> {

    public Optional<SouscriptionEntity> trouverActive(UUID contexteId, String moduleId) {
        return find("contexteId = ?1 and moduleId = ?2 and tracabilite.supprimeLe is null",
                contexteId, moduleId).firstResultOptional();
    }

    public boolean existeActive(UUID contexteId, String moduleId) {
        return trouverActive(contexteId, moduleId).isPresent();
    }

    public List<SouscriptionEntity> listerPourContexte(UUID contexteId) {
        return list("contexteId = ?1 order by tracabilite.creeLe desc", contexteId);
    }
}
