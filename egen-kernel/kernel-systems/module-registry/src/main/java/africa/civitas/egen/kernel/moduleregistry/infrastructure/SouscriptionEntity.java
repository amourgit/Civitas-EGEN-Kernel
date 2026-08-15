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
@Table(name = "modreg_souscription")
public class SouscriptionEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "contexte_id", nullable = false)
    public UUID contexteId;

    @Column(name = "module_id", nullable = false, length = 100)
    public String moduleId;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
