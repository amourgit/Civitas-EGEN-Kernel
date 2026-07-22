package africa.civitas.egen.modules.business.referencedata.impl.infrastructure;

import africa.civitas.egen.modules.business.referencedata.impl.domain.MandatModeleEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MandatModeleRepository implements PanacheRepositoryBase<MandatModeleEntity, UUID> {

    public List<MandatModeleEntity> listByModeleSectoriel(UUID modeleSectorialId) {
        return list("modeleSectorialId", modeleSectorialId);
    }
}
