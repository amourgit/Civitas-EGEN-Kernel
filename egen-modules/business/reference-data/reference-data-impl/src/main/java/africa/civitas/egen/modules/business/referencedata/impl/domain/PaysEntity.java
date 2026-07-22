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
@Table(name = "refdata_pays")
public class PaysEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "code_alpha2", nullable = false, unique = true, length = 2)
    public String codeAlpha2;

    @Column(name = "code_alpha3", nullable = false, unique = true, length = 3)
    public String codeAlpha3;

    @Column(name = "nom_officiel", nullable = false, length = 200)
    public String nomOfficiel;

    @Column(name = "nom_usuel", nullable = false, length = 100)
    public String nomUsuel;

    @Column(name = "indicatif_telephonique", nullable = false, length = 6)
    public String indicatifTelephonique;

    @Column(name = "code_devise_par_defaut", nullable = false, length = 3)
    public String codeDeviseParDefaut;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
