package africa.civitas.egen.modules.business.organization.impl.politique.infrastructure;

import africa.civitas.egen.modules.business.organization.api.politique.domain.Derogation;
import africa.civitas.egen.modules.business.organization.impl.politique.domain.DerogationEntity;

public final class DerogationMapper {

    private DerogationMapper() {
    }

    public static Derogation toDomain(DerogationEntity entity) {
        return new Derogation(
                entity.id, entity.politiqueId, entity.celluleDerogatoireId, entity.valeur,
                entity.justification, entity.dateEntreeVigueur, entity.dateFin,
                entity.tracabilite.toDomain());
    }
}
