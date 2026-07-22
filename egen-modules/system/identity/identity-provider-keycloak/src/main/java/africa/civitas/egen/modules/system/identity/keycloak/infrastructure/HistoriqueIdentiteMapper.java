package africa.civitas.egen.modules.system.identity.keycloak.infrastructure;

import africa.civitas.egen.modules.system.identity.api.domain.HistoriqueIdentite;
import africa.civitas.egen.modules.system.identity.keycloak.domain.HistoriqueIdentiteEntity;

public final class HistoriqueIdentiteMapper {

    private HistoriqueIdentiteMapper() {
    }

    public static HistoriqueIdentite toDomain(HistoriqueIdentiteEntity entity) {
        return new HistoriqueIdentite(
                entity.id,
                entity.personneId,
                entity.typeChangement,
                entity.valeurPrecedente,
                entity.valeurNouvelle,
                entity.pieceJustificativeRef,
                entity.dateEffet,
                entity.tracabilite.toDomain());
    }
}
