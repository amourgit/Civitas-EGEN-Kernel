package africa.civitas.egen.kernel.referencedata.impl.infrastructure;

import africa.civitas.egen.kernel.referencedata.impl.domain.TypeCelluleModeleEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class TypeCelluleModeleRepository implements PanacheRepositoryBase<TypeCelluleModeleEntity, UUID> {

    public List<TypeCelluleModeleEntity> listByModeleSectoriel(UUID modeleSectorialId) {
        return list("modeleSectorialId", modeleSectorialId);
    }
}
