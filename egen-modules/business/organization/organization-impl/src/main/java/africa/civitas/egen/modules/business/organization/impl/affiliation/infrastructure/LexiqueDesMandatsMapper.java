package africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure;

import africa.civitas.egen.modules.business.organization.api.affiliation.domain.LexiqueDesMandats;
import africa.civitas.egen.modules.business.organization.impl.affiliation.domain.LexiqueDesMandatsEntity;

public final class LexiqueDesMandatsMapper {

    private LexiqueDesMandatsMapper() {
    }

    public static LexiqueDesMandats toDomain(LexiqueDesMandatsEntity entity) {
        return new LexiqueDesMandats(
                entity.id, entity.organisationId, entity.nom, entity.description,
                entity.modeleSectorielOrigineId, entity.tracabilite.toDomain());
    }
}
