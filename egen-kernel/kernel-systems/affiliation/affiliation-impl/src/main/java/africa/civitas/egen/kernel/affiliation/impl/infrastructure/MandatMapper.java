package africa.civitas.egen.kernel.affiliation.impl.infrastructure;

import africa.civitas.egen.kernel.affiliation.api.domain.Mandat;
import africa.civitas.egen.kernel.affiliation.impl.domain.MandatEntity;

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
