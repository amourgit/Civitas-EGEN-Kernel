package africa.civitas.egen.kernel.organization.impl.infrastructure;

import africa.civitas.egen.kernel.organization.api.domain.Tutelle;
import africa.civitas.egen.kernel.organization.impl.domain.TutelleEntity;

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
