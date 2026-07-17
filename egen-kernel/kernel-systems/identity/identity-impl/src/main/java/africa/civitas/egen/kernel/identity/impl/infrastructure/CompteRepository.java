package africa.civitas.egen.kernel.identity.impl.infrastructure;

import africa.civitas.egen.kernel.identity.impl.domain.CompteEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CompteRepository implements PanacheRepositoryBase<CompteEntity, UUID> {

    public Optional<CompteEntity> findByIdentifiantConnexion(String identifiantConnexion) {
        return find("identifiantConnexion", identifiantConnexion).firstResultOptional();
    }

    public Optional<CompteEntity> findByKeycloakId(String keycloakId) {
        return find("keycloakId", keycloakId).firstResultOptional();
    }

    public List<CompteEntity> listByPersonneId(UUID personneId) {
        return list("personneId", personneId);
    }
}
