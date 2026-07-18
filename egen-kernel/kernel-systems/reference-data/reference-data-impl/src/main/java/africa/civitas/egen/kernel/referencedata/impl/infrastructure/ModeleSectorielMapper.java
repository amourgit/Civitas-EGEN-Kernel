package africa.civitas.egen.kernel.referencedata.impl.infrastructure;

import africa.civitas.egen.kernel.referencedata.api.domain.ModeleSectoriel;
import africa.civitas.egen.kernel.referencedata.impl.domain.ModeleSectorielEntity;

public final class ModeleSectorielMapper {

    private ModeleSectorielMapper() {
    }

    public static ModeleSectoriel toDomain(ModeleSectorielEntity entity) {
        return new ModeleSectoriel(
                entity.id, entity.nomSecteur, entity.description, entity.versionModele,
                entity.statut, entity.tracabilite.toDomain());
    }
}
