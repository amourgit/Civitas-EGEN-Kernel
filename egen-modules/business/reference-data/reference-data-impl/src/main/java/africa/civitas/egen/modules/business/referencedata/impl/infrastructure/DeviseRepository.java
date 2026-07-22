package africa.civitas.egen.modules.business.referencedata.impl.infrastructure;

import africa.civitas.egen.modules.business.referencedata.impl.domain.DeviseEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DeviseRepository implements PanacheRepositoryBase<DeviseEntity, UUID> {

    public Optional<DeviseEntity> findByCode(String codeIso4217) {
        return find("codeIso4217", codeIso4217).firstResultOptional();
    }
}
