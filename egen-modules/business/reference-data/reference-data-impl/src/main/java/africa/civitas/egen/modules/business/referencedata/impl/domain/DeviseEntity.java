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
@Table(name = "refdata_devise")
public class DeviseEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "code_iso_4217", nullable = false, unique = true, length = 3)
    public String codeIso4217;

    @Column(name = "symbole", nullable = false, length = 10)
    public String symbole;

    @Column(name = "nom", nullable = false, length = 100)
    public String nom;

    @Column(name = "nombre_decimales", nullable = false)
    public int nombreDecimales;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
