package africa.civitas.egen.kernel.moduleregistry.infrastructure;

import africa.civitas.egen.kernel.domain.module.ModuleId;
import africa.civitas.egen.kernel.domain.module.Souscription;

public final class SouscriptionMapper {

    private SouscriptionMapper() {
    }

    public static Souscription toDomain(SouscriptionEntity entity) {
        return new Souscription(
                entity.id, entity.contexteId, new ModuleId(entity.moduleId),
                entity.tracabilite.toDomain());
    }
}
