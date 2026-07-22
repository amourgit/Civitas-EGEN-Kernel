package africa.civitas.egen.modules.business.organization.impl.infrastructure;

import africa.civitas.egen.modules.business.organization.impl.domain.OrganisationEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OrganisationRepository implements PanacheRepositoryBase<OrganisationEntity, UUID> {

    public Optional<OrganisationEntity> findByIdentifiantJuridique(String identifiantJuridique) {
        return find("identifiantJuridique", identifiantJuridique).firstResultOptional();
    }
}
