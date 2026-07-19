package africa.civitas.egen.kernel.organization.impl.infrastructure;

import africa.civitas.egen.kernel.organization.impl.domain.TypeCelluleEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TypeCelluleRepository implements PanacheRepositoryBase<TypeCelluleEntity, UUID> {

    public List<TypeCelluleEntity> listByLexique(UUID lexiqueId) {
        return list("lexiqueId", lexiqueId);
    }
}
