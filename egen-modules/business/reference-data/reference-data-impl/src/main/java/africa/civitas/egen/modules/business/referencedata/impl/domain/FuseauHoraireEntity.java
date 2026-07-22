package africa.civitas.egen.modules.business.referencedata.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "refdata_fuseau_horaire")
public class FuseauHoraireEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "identifiant_iana", nullable = false, unique = true, length = 100)
    public String identifiantIana;

    @Column(name = "libelle_usuel", nullable = false, length = 150)
    public String libelleUsuel;

    @Column(name = "decalage_utc_reference", nullable = false, length = 6)
    public String decalageUtcReference;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
