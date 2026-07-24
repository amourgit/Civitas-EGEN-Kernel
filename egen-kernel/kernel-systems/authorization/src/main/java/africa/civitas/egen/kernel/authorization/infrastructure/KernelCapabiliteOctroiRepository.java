package africa.civitas.egen.kernel.authorization.infrastructure;

import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class KernelCapabiliteOctroiRepository implements PanacheRepositoryBase<KernelCapabiliteOctroiEntity, UUID> {

    /** @return l'octroi actif (non revoque) pour ce sujet et cette capacite, s'il existe. */
    public Optional<KernelCapabiliteOctroiEntity> trouverActif(UUID sujetId, KernelCapability capacite) {
        return find("sujetId = ?1 and capacite = ?2 and tracabilite.supprimeLe is null", sujetId, capacite)
                .firstResultOptional();
    }

    public boolean existeOctroiActif(UUID sujetId, KernelCapability capacite) {
        return trouverActif(sujetId, capacite).isPresent();
    }

    public List<KernelCapabiliteOctroiEntity> listerPourSujet(UUID sujetId) {
        return list("sujetId = ?1 order by tracabilite.creeLe desc", sujetId);
    }
}
