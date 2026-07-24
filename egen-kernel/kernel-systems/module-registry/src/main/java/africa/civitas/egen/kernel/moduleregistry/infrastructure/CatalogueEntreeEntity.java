package africa.civitas.egen.kernel.moduleregistry.infrastructure;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "modreg_catalogue_entree")
public class CatalogueEntreeEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "module_id", nullable = false, length = 100, unique = true)
    public String moduleId;

    @Column(name = "nom", nullable = false, length = 150)
    public String nom;

    @Column(name = "description", nullable = false)
    public String description;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
