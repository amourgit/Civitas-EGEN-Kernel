package africa.civitas.egen.kernel.referencedata.impl.infrastructure;

import africa.civitas.egen.kernel.referencedata.api.domain.MandatModele;
import africa.civitas.egen.kernel.referencedata.impl.domain.MandatModeleEntity;

public final class MandatModeleMapper {

    private MandatModeleMapper() {
    }

    public static MandatModele toDomain(MandatModeleEntity entity) {
        return new MandatModele(
                entity.id, entity.modeleSectorialId, entity.libelleSuggere,
                entity.niveauAutoriteIndicatif, entity.descriptionResponsabilites,
                entity.tracabilite.toDomain());
    }
}
