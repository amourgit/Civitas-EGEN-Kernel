package africa.civitas.egen.kernel.affiliation.impl.infrastructure;

import africa.civitas.egen.kernel.affiliation.impl.domain.AffectationEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AffectationRepository implements PanacheRepositoryBase<AffectationEntity, UUID> {

    public List<AffectationEntity> listByPersonne(UUID personneId) {
        return list("personneId", personneId);
    }

    public List<AffectationEntity> listByCellule(UUID celluleId) {
        return list("celluleId", celluleId);
    }
}
