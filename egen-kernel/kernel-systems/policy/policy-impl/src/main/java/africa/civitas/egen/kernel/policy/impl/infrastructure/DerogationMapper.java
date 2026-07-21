package africa.civitas.egen.kernel.policy.impl.infrastructure;

import africa.civitas.egen.kernel.policy.api.domain.Derogation;
import africa.civitas.egen.kernel.policy.impl.domain.DerogationEntity;

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
