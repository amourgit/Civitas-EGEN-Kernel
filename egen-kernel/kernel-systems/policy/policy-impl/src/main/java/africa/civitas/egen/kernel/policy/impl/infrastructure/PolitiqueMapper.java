package africa.civitas.egen.kernel.policy.impl.infrastructure;

import africa.civitas.egen.kernel.policy.api.domain.Politique;
import africa.civitas.egen.kernel.policy.impl.domain.PolitiqueEntity;

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
