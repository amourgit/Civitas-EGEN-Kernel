package africa.civitas.egen.kernel.affiliation.impl.infrastructure;

import africa.civitas.egen.kernel.affiliation.api.domain.LexiqueDesMandats;
import africa.civitas.egen.kernel.affiliation.impl.domain.LexiqueDesMandatsEntity;

public final class LexiqueDesMandatsMapper {

    private LexiqueDesMandatsMapper() {
    }

    public static LexiqueDesMandats toDomain(LexiqueDesMandatsEntity entity) {
        return new LexiqueDesMandats(
                entity.id, entity.organisationId, entity.nom, entity.description,
                entity.modeleSectorielOrigineId, entity.tracabilite.toDomain());
    }
}
