package africa.civitas.egen.kernel.organization.impl.infrastructure;

import africa.civitas.egen.kernel.organization.impl.domain.OrganisationEntity;
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
