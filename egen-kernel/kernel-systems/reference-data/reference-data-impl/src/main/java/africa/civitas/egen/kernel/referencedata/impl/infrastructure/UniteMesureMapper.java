package africa.civitas.egen.kernel.referencedata.impl.infrastructure;

import africa.civitas.egen.kernel.referencedata.api.domain.UniteMesure;
import africa.civitas.egen.kernel.referencedata.impl.domain.UniteMesureEntity;

public final class UniteMesureMapper {

    private UniteMesureMapper() {
    }

    public static UniteMesure toDomain(UniteMesureEntity entity) {
        return new UniteMesure(
                entity.id, entity.nom, entity.symbole, entity.categorie,
                entity.facteurConversion, entity.tracabilite.toDomain());
    }
}
