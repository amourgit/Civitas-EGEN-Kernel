package africa.civitas.egen.modules.business.referencedata.impl.infrastructure;

import africa.civitas.egen.modules.business.referencedata.api.domain.ModeleSectoriel;
import africa.civitas.egen.modules.business.referencedata.impl.domain.ModeleSectorielEntity;

public final class ModeleSectorielMapper {

    private ModeleSectorielMapper() {
    }

    public static ModeleSectoriel toDomain(ModeleSectorielEntity entity) {
        return new ModeleSectoriel(
                entity.id, entity.nomSecteur, entity.description, entity.versionModele,
                entity.statut, entity.tracabilite.toDomain());
    }
}
