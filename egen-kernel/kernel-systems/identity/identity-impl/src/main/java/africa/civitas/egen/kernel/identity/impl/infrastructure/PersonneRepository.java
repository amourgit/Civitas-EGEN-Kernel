package africa.civitas.egen.kernel.identity.impl.infrastructure;

import africa.civitas.egen.kernel.identity.impl.domain.PersonneEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PersonneRepository implements PanacheRepositoryBase<PersonneEntity, UUID> {

    public Optional<PersonneEntity> findByIdentifiantCivilReference(String identifiantCivilReference) {
        return find("identifiantCivilReference", identifiantCivilReference).firstResultOptional();
    }

    public List<PersonneEntity> listActives() {
        return list("tracabilite.supprimeLe is null");
    }
}
