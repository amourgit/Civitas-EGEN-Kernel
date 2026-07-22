package africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure;

import africa.civitas.egen.modules.business.organization.impl.affiliation.domain.LexiqueDesMandatsEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class LexiqueDesMandatsRepository implements PanacheRepositoryBase<LexiqueDesMandatsEntity, UUID> {

    public List<LexiqueDesMandatsEntity> listByOrganisation(UUID organisationId) {
        return list("organisationId", organisationId);
    }
}
