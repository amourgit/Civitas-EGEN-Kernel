package africa.civitas.egen.modules.business.organization.impl.infrastructure;

import africa.civitas.egen.modules.business.organization.api.domain.Tutelle;
import africa.civitas.egen.modules.business.organization.impl.domain.TutelleEntity;

public final class TutelleMapper {

    private TutelleMapper() {
    }

    public static Tutelle toDomain(TutelleEntity entity) {
        return new Tutelle(
                entity.id, entity.celluleRacineId, entity.organisationId, entity.nature,
                entity.tutellePrincipale, entity.dateDebut, entity.dateFin,
                entity.acteJustificatifRef, entity.tracabilite.toDomain());
    }
}
