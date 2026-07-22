package africa.civitas.egen.modules.business.referencedata.impl.infrastructure;

import africa.civitas.egen.modules.business.referencedata.api.domain.UniteMesure;
import africa.civitas.egen.modules.business.referencedata.impl.domain.UniteMesureEntity;

public final class UniteMesureMapper {

    private UniteMesureMapper() {
    }

    public static UniteMesure toDomain(UniteMesureEntity entity) {
        return new UniteMesure(
                entity.id, entity.nom, entity.symbole, entity.categorie,
                entity.facteurConversion, entity.tracabilite.toDomain());
    }
}
