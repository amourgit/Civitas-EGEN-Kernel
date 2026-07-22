package africa.civitas.egen.modules.system.identity.keycloak.infrastructure;

import africa.civitas.egen.modules.system.identity.keycloak.domain.HistoriqueIdentiteEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class HistoriqueIdentiteRepository implements PanacheRepositoryBase<HistoriqueIdentiteEntity, UUID> {

    public List<HistoriqueIdentiteEntity> listByPersonneIdOrdonneParDateEffet(UUID personneId) {
        return list("personneId = ?1 order by dateEffet asc, tracabilite.creeLe asc", personneId);
    }
}
