package africa.civitas.egen.modules.business.organization.impl.affiliation.infrastructure;

import africa.civitas.egen.modules.business.organization.impl.affiliation.domain.MandatEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MandatRepository implements PanacheRepositoryBase<MandatEntity, UUID> {

    public List<MandatEntity> listByLexique(UUID lexiqueMandatsId) {
        return list("lexiqueMandatsId", lexiqueMandatsId);
    }
}
