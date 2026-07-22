package africa.civitas.egen.modules.business.organization.impl.infrastructure;

import africa.civitas.egen.modules.business.organization.impl.domain.TutelleEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TutelleRepository implements PanacheRepositoryBase<TutelleEntity, UUID> {

    public List<TutelleEntity> listByCelluleRacine(UUID celluleRacineId) {
        return list("celluleRacineId", celluleRacineId);
    }

    public Optional<TutelleEntity> findPrincipaleByCelluleRacine(UUID celluleRacineId) {
        return find("celluleRacineId = ?1 and tutellePrincipale = true", celluleRacineId)
                .firstResultOptional();
    }
}
