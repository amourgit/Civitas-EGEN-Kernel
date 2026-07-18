package africa.civitas.egen.kernel.referencedata.impl.infrastructure;

import africa.civitas.egen.kernel.referencedata.api.domain.CategorieUniteMesure;
import africa.civitas.egen.kernel.referencedata.impl.domain.UniteMesureEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UniteMesureRepository implements PanacheRepositoryBase<UniteMesureEntity, UUID> {

    public Optional<UniteMesureEntity> findByNom(String nom) {
        return find("nom", nom).firstResultOptional();
    }

    public List<UniteMesureEntity> listByCategorie(CategorieUniteMesure categorie) {
        return list("categorie", categorie);
    }
}
