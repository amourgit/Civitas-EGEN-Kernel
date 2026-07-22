package africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure;

import africa.civitas.egen.modules.business.organization.api.affiliation.domain.Mandat;
import africa.civitas.egen.modules.business.organization.impl.affiliation.domain.MandatEntity;

public final class MandatMapper {

    private MandatMapper() {
    }

    public static Mandat toDomain(MandatEntity entity) {
        return new Mandat(
                entity.id, entity.lexiqueMandatsId, entity.libelle, entity.description,
                entity.niveauAutoriteIndicatif, entity.mandatsSupervisesIds,
                entity.mandatModeleOrigineId, entity.statut, entity.tracabilite.toDomain());
    }
}
