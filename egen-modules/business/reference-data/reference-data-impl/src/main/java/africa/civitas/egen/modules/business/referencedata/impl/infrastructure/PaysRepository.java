package africa.civitas.egen.modules.business.referencedata.impl.infrastructure;

import africa.civitas.egen.modules.business.referencedata.impl.domain.PaysEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PaysRepository implements PanacheRepositoryBase<PaysEntity, UUID> {

    public Optional<PaysEntity> findByCodeAlpha2(String codeAlpha2) {
        return find("codeAlpha2", codeAlpha2).firstResultOptional();
    }
}
