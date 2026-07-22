package africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure;

import africa.civitas.egen.modules.business.organization.api.affiliation.domain.Delegation;
import africa.civitas.egen.modules.business.organization.impl.affiliation.domain.DelegationEntity;

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
