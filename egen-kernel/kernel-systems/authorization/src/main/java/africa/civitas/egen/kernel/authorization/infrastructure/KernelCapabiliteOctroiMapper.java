package africa.civitas.egen.kernel.authorization.infrastructure;

import africa.civitas.egen.kernel.authorization.domain.KernelCapabiliteOctroi;

public final class KernelCapabiliteOctroiMapper {

    private KernelCapabiliteOctroiMapper() {
    }

    public static KernelCapabiliteOctroi toDomain(KernelCapabiliteOctroiEntity entity) {
        return new KernelCapabiliteOctroi(
                entity.id, entity.sujetId, entity.capacite, entity.tracabilite.toDomain());
    }
}
