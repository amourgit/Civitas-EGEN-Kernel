package africa.civitas.egen.kernel.referencedata.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.referencedata.api.domain.SensEcriture;
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
@Table(name = "refdata_langue")
public class LangueEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "code_iso_639", nullable = false, unique = true, length = 3)
    public String codeIso639;

    @Column(name = "nom_officiel", nullable = false, length = 100)
    public String nomOfficiel;

    @Column(name = "nom_natif", nullable = false, length = 100)
    public String nomNatif;

    @Enumerated(EnumType.STRING)
    @Column(name = "sens_ecriture", nullable = false, length = 20)
    public SensEcriture sensEcriture;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
