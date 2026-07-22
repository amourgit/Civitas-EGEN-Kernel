package africa.civitas.egen.modules.business.organization.impl.infrastructure;

import africa.civitas.egen.modules.business.organization.api.domain.TypeCellule;
import africa.civitas.egen.modules.business.organization.impl.domain.TypeCelluleEntity;

public final class TypeCelluleMapper {

    private TypeCelluleMapper() {
    }

    public static TypeCellule toDomain(TypeCelluleEntity entity) {
        return new TypeCellule(
                entity.id, entity.lexiqueId, entity.libelle, entity.description,
                entity.niveauIndicatif, entity.typesParentsAutorisesIds,
                entity.typeCelluleModeleOrigineId, entity.statut, entity.tracabilite.toDomain());
    }
}
