package africa.civitas.egen.kernel.affiliation.impl.infrastructure;

import africa.civitas.egen.kernel.affiliation.api.domain.Delegation;
import africa.civitas.egen.kernel.affiliation.impl.domain.DelegationEntity;

public final class DelegationMapper {

    private DelegationMapper() {
    }

    public static Delegation toDomain(DelegationEntity entity) {
        return new Delegation(
                entity.id, entity.affectationOrigineId, entity.personneBeneficiaireId,
                entity.etendue, entity.actionsCouvertes, entity.dateDebut, entity.dateFin,
                entity.motif, entity.statut, entity.tracabilite.toDomain());
    }
}
