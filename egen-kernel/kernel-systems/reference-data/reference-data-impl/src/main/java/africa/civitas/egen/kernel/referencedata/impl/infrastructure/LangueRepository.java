package africa.civitas.egen.kernel.referencedata.impl.infrastructure;

import africa.civitas.egen.kernel.referencedata.impl.domain.LangueEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LangueRepository implements PanacheRepositoryBase<LangueEntity, UUID> {

    public Optional<LangueEntity> findByCode(String codeIso639) {
        return find("codeIso639", codeIso639).firstResultOptional();
    }
}
