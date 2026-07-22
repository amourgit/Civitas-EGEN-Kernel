package africa.civitas.egen.modules.business.referencedata.impl.infrastructure;

import africa.civitas.egen.modules.business.referencedata.api.domain.TypeCelluleModele;
import africa.civitas.egen.modules.business.referencedata.impl.domain.TypeCelluleModeleEntity;

public final class TypeCelluleModeleMapper {

    private TypeCelluleModeleMapper() {
    }

    public static TypeCelluleModele toDomain(TypeCelluleModeleEntity entity) {
        return new TypeCelluleModele(
                entity.id, entity.modeleSectorialId, entity.libelleMetierSuggere,
                entity.niveauIndicatifSuggere, entity.typeParentSuggereId, entity.tracabilite.toDomain());
    }
}
