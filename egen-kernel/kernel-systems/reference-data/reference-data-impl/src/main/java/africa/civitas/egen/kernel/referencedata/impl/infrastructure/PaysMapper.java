package africa.civitas.egen.kernel.referencedata.impl.infrastructure;

import africa.civitas.egen.kernel.referencedata.api.domain.Pays;
import africa.civitas.egen.kernel.referencedata.impl.domain.PaysEntity;

public final class PaysMapper {

    private PaysMapper() {
    }

    public static Pays toDomain(PaysEntity entity) {
        return new Pays(
                entity.id, entity.codeAlpha2, entity.codeAlpha3, entity.nomOfficiel,
                entity.nomUsuel, entity.indicatifTelephonique, entity.codeDeviseParDefaut,
                entity.tracabilite.toDomain());
    }
}
