package africa.civitas.egen.kernel.organization.impl.infrastructure;

import africa.civitas.egen.kernel.organization.impl.domain.CelluleEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CelluleRepository implements PanacheRepositoryBase<CelluleEntity, UUID> {

    public List<CelluleEntity> listByOrganisation(UUID organisationId) {
        return list("organisationId", organisationId);
    }

    public List<CelluleEntity> listEtablissements(UUID organisationId) {
        return list("organisationId = ?1 and celluleParentId is null", organisationId);
    }

    public Optional<CelluleEntity> findByOrganisationAndCodeInterne(UUID organisationId, String codeInterne) {
        return find("organisationId = ?1 and codeInterne = ?2", organisationId, codeInterne)
                .firstResultOptional();
    }

    public List<CelluleEntity> listByIds(List<UUID> ids) {
        return list("id in ?1", ids);
    }
}
