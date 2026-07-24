package africa.civitas.egen.kernel.moduleregistry.infrastructure;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class SouscriptionRepository implements PanacheRepositoryBase<SouscriptionEntity, UUID> {

    public Optional<SouscriptionEntity> trouverActive(UUID organisationId, String moduleId) {
        return find("organisationId = ?1 and moduleId = ?2 and tracabilite.supprimeLe is null",
                organisationId, moduleId).firstResultOptional();
    }

    public boolean existeActive(UUID organisationId, String moduleId) {
        return trouverActive(organisationId, moduleId).isPresent();
    }

    public List<SouscriptionEntity> listerPourOrganisation(UUID organisationId) {
        return list("organisationId = ?1 order by tracabilite.creeLe desc", organisationId);
    }
}
