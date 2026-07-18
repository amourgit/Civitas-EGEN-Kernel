package africa.civitas.egen.kernel.referencedata.impl.infrastructure;

import africa.civitas.egen.kernel.referencedata.impl.domain.FuseauHoraireEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class FuseauHoraireRepository implements PanacheRepositoryBase<FuseauHoraireEntity, UUID> {

    public Optional<FuseauHoraireEntity> findByIdentifiantIana(String identifiantIana) {
        return find("identifiantIana", identifiantIana).firstResultOptional();
    }
}
