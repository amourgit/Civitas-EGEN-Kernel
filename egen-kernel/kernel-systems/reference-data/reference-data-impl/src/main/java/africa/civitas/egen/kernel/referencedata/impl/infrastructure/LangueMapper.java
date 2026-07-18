package africa.civitas.egen.kernel.referencedata.impl.infrastructure;

import africa.civitas.egen.kernel.referencedata.api.domain.Langue;
import africa.civitas.egen.kernel.referencedata.impl.domain.LangueEntity;

public final class LangueMapper {

    private LangueMapper() {
    }

    public static Langue toDomain(LangueEntity entity) {
        return new Langue(
                entity.id, entity.codeIso639, entity.nomOfficiel, entity.nomNatif,
                entity.sensEcriture, entity.tracabilite.toDomain());
    }
}
