package africa.civitas.egen.kernel.referencedata.impl.infrastructure;

import africa.civitas.egen.kernel.referencedata.api.domain.TypeCelluleModele;
import africa.civitas.egen.kernel.referencedata.impl.domain.TypeCelluleModeleEntity;

public final class TypeCelluleModeleMapper {

    private TypeCelluleModeleMapper() {
    }

    public static TypeCelluleModele toDomain(TypeCelluleModeleEntity entity) {
        return new TypeCelluleModele(
                entity.id, entity.modeleSectorialId, entity.libelleMetierSuggere,
                entity.niveauIndicatifSuggere, entity.typeParentSuggereId, entity.tracabilite.toDomain());
    }
}
