package africa.civitas.egen.modules.business.organization.impl.infrastructure;

import africa.civitas.egen.modules.business.organization.api.domain.SuccessionOrganisationnelle;
import africa.civitas.egen.modules.business.organization.impl.domain.SuccessionOrganisationnelleEntity;

public final class SuccessionOrganisationnelleMapper {

    private SuccessionOrganisationnelleMapper() {
    }

    public static SuccessionOrganisationnelle toDomain(SuccessionOrganisationnelleEntity entity) {
        return new SuccessionOrganisationnelle(
                entity.id, entity.celluleOrigineIds, entity.celluleHeritiereIds,
                entity.nature, entity.dateEffet, entity.motifDecisionReference,
                entity.tracabilite.toDomain());
    }
}
