package africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure;

import africa.civitas.egen.modules.business.organization.impl.affiliation.domain.DelegationEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DelegationRepository implements PanacheRepositoryBase<DelegationEntity, UUID> {

    public List<DelegationEntity> listByAffectationOrigine(UUID affectationOrigineId) {
        return list("affectationOrigineId", affectationOrigineId);
    }

    public List<DelegationEntity> listByPersonneBeneficiaire(UUID personneBeneficiaireId) {
        return list("personneBeneficiaireId", personneBeneficiaireId);
    }
}
