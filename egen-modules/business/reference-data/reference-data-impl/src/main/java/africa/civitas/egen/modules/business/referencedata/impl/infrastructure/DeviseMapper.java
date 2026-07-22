package africa.civitas.egen.modules.business.referencedata.impl.infrastructure;

import africa.civitas.egen.modules.business.referencedata.api.domain.Devise;
import africa.civitas.egen.modules.business.referencedata.impl.domain.DeviseEntity;

public final class DeviseMapper {

    private DeviseMapper() {
    }

    public static Devise toDomain(DeviseEntity entity) {
        return new Devise(
                entity.id, entity.codeIso4217, entity.symbole, entity.nom,
                entity.nombreDecimales, entity.tracabilite.toDomain());
    }
}
