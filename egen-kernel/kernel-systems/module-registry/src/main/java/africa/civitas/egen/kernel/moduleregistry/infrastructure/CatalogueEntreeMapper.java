package africa.civitas.egen.kernel.moduleregistry.infrastructure;

import africa.civitas.egen.kernel.domain.module.CatalogueEntree;
import africa.civitas.egen.kernel.domain.module.ModuleId;

public final class CatalogueEntreeMapper {

    private CatalogueEntreeMapper() {
    }

    public static CatalogueEntree toDomain(CatalogueEntreeEntity entity) {
        return new CatalogueEntree(
                entity.id, new ModuleId(entity.moduleId), entity.nom, entity.description,
                entity.tracabilite.toDomain());
    }
}
