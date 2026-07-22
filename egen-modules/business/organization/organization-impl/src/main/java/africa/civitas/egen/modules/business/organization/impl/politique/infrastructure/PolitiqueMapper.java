package africa.civitas.egen.modules.business.organization.impl.politique.infrastructure;

import africa.civitas.egen.modules.business.organization.api.politique.domain.Politique;
import africa.civitas.egen.modules.business.organization.impl.politique.domain.PolitiqueEntity;

public final class PolitiqueMapper {

    private PolitiqueMapper() {
    }

    public static Politique toDomain(PolitiqueEntity entity) {
        return new Politique(
                entity.id, entity.contexteId, entity.contexteNature, entity.domaine,
                entity.nomRegle, entity.valeur, entity.statut, entity.dateEntreeVigueur,
                entity.tracabilite.toDomain());
    }
}
