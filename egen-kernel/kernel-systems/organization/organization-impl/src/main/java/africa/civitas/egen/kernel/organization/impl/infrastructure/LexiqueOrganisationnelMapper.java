package africa.civitas.egen.kernel.organization.impl.infrastructure;

import africa.civitas.egen.kernel.organization.api.domain.LexiqueOrganisationnel;
import africa.civitas.egen.kernel.organization.impl.domain.LexiqueOrganisationnelEntity;

public final class LexiqueOrganisationnelMapper {

    private LexiqueOrganisationnelMapper() {
    }

    public static LexiqueOrganisationnel toDomain(LexiqueOrganisationnelEntity entity) {
        return new LexiqueOrganisationnel(
                entity.id, entity.organisationId, entity.nom, entity.description,
                entity.modeleSectorielOrigineId, entity.tracabilite.toDomain());
    }
}
