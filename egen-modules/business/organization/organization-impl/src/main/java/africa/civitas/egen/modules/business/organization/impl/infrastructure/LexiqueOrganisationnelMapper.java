package africa.civitas.egen.modules.business.organization.impl.infrastructure;

import africa.civitas.egen.modules.business.organization.api.domain.LexiqueOrganisationnel;
import africa.civitas.egen.modules.business.organization.impl.domain.LexiqueOrganisationnelEntity;

public final class LexiqueOrganisationnelMapper {

    private LexiqueOrganisationnelMapper() {
    }

    public static LexiqueOrganisationnel toDomain(LexiqueOrganisationnelEntity entity) {
        return new LexiqueOrganisationnel(
                entity.id, entity.organisationId, entity.nom, entity.description,
                entity.modeleSectorielOrigineId, entity.tracabilite.toDomain());
    }
}
