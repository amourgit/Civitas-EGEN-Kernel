package africa.civitas.egen.modules.business.organization.impl.infrastructure;

import africa.civitas.egen.modules.business.organization.impl.domain.SuccessionOrganisationnelleEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class SuccessionOrganisationnelleRepository
        implements PanacheRepositoryBase<SuccessionOrganisationnelleEntity, UUID> {

    public List<SuccessionOrganisationnelleEntity> listByCellule(UUID celluleId) {
        return list("?1 member of celluleOrigineIds or ?1 member of celluleHeritiereIds", celluleId);
    }
}
