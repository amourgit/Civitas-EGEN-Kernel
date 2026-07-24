package africa.civitas.egen.kernel.moduleregistry.infrastructure;

import africa.civitas.egen.kernel.domain.module.Activation;
import africa.civitas.egen.kernel.domain.module.ModuleId;

public final class ActivationMapper {

    private ActivationMapper() {
    }

    public static Activation toDomain(ActivationEntity entity) {
        return new Activation(
                entity.id, entity.celluleId, new ModuleId(entity.moduleId),
                entity.tracabilite.toDomain());
    }
}
