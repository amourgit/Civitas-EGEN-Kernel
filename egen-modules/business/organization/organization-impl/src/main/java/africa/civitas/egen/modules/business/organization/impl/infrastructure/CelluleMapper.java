package africa.civitas.egen.modules.business.organization.impl.infrastructure;

import africa.civitas.egen.modules.business.organization.api.domain.Cellule;
import africa.civitas.egen.modules.business.organization.impl.domain.CelluleEntity;

public final class CelluleMapper {

    private CelluleMapper() {
    }

    public static Cellule toDomain(CelluleEntity entity) {
        return new Cellule(
                entity.id, entity.organisationId, entity.celluleParentId, entity.typeCelluleId,
                entity.nom, entity.codeInterne, entity.description, entity.codePaysLocalisation,
                entity.adressePhysique, entity.statut, entity.validDu, entity.validAu,
                entity.tracabilite.toDomain());
    }
}
