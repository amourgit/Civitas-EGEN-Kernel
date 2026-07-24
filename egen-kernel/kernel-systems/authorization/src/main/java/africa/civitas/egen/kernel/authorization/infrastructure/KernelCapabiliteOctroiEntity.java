package africa.civitas.egen.kernel.authorization.infrastructure;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.sdk.permission.authorization.KernelCapability;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "authz_capacite_octroi")
public class KernelCapabiliteOctroiEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "sujet_id", nullable = false)
    public UUID sujetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "capacite", nullable = false, length = 40)
    public KernelCapability capacite;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
