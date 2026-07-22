package africa.civitas.egen.modules.business.organization.impl.infrastructure;

import africa.civitas.egen.modules.business.organization.impl.domain.LexiqueOrganisationnelEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class LexiqueOrganisationnelRepository implements PanacheRepositoryBase<LexiqueOrganisationnelEntity, UUID> {

    public List<LexiqueOrganisationnelEntity> listByOrganisation(UUID organisationId) {
        return list("organisationId", organisationId);
    }
}
