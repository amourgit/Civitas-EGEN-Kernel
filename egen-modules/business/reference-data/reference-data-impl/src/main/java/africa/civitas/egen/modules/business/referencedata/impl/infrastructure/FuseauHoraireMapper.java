package africa.civitas.egen.modules.business.referencedata.impl.infrastructure;

import africa.civitas.egen.modules.business.referencedata.api.domain.FuseauHoraire;
import africa.civitas.egen.modules.business.referencedata.impl.domain.FuseauHoraireEntity;

public final class FuseauHoraireMapper {

    private FuseauHoraireMapper() {
    }

    public static FuseauHoraire toDomain(FuseauHoraireEntity entity) {
        return new FuseauHoraire(
                entity.id, entity.identifiantIana, entity.libelleUsuel,
                entity.decalageUtcReference, entity.tracabilite.toDomain());
    }
}
