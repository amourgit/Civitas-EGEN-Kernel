package africa.civitas.egen.kernel.moduleregistry.infrastructure;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CatalogueEntreeRepository implements PanacheRepositoryBase<CatalogueEntreeEntity, UUID> {

    public Optional<CatalogueEntreeEntity> trouverParModuleId(String moduleId) {
        return find("moduleId", moduleId).firstResultOptional();
    }

    public boolean existeParModuleId(String moduleId) {
        return trouverParModuleId(moduleId).isPresent();
    }
}
