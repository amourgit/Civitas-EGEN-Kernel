package africa.civitas.egen.modules.business.organization.impl.politique.infrastructure;

import africa.civitas.egen.modules.business.organization.impl.politique.domain.PolitiqueEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PolitiqueRepository implements PanacheRepositoryBase<PolitiqueEntity, UUID> {

    public List<PolitiqueEntity> listByContexte(UUID contexteId) {
        return list("contexteId", contexteId);
    }
}
