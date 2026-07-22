package africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure;

import africa.civitas.egen.modules.business.organization.api.affiliation.domain.Affectation;
import africa.civitas.egen.modules.business.organization.impl.affiliation.domain.AffectationEntity;

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
