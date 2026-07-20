package africa.civitas.egen.kernel.affiliation.impl.infrastructure;

import africa.civitas.egen.kernel.affiliation.api.domain.Affectation;
import africa.civitas.egen.kernel.affiliation.impl.domain.AffectationEntity;

public final class AffectationMapper {

    private AffectationMapper() {
    }

    public static Affectation toDomain(AffectationEntity entity) {
        return new Affectation(
                entity.id, entity.personneId, entity.celluleId, entity.mandatId,
                entity.quotiteEngagement, entity.dateDebut, entity.dateFin, entity.statut,
                entity.motifFin, entity.tracabilite.toDomain());
    }
}
