package africa.civitas.egen.kernel.identity.impl.infrastructure;

import africa.civitas.egen.kernel.identity.api.domain.Compte;
import africa.civitas.egen.kernel.identity.impl.domain.CompteEntity;

public final class CompteMapper {

    private CompteMapper() {
    }

    public static Compte toDomain(CompteEntity entity) {
        return new Compte(
                entity.id,
                entity.keycloakId,
                entity.identifiantConnexion,
                entity.typeCompte,
                entity.statutCompte,
                entity.derniereConnexionReussie,
                entity.methodeAuthentification,
                entity.personneId,
                entity.dateExpirationPrevue,
                entity.tracabilite.toDomain());
    }
}
