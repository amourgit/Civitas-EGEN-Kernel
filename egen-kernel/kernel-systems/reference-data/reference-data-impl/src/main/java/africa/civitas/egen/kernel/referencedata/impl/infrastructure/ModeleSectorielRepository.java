package africa.civitas.egen.kernel.referencedata.impl.infrastructure;

import africa.civitas.egen.kernel.referencedata.api.domain.StatutModeleSectoriel;
import africa.civitas.egen.kernel.referencedata.impl.domain.ModeleSectorielEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ModeleSectorielRepository implements PanacheRepositoryBase<ModeleSectorielEntity, UUID> {

    public List<ModeleSectorielEntity> listByStatut(StatutModeleSectoriel statut) {
        return list("statut", statut);
    }
}
