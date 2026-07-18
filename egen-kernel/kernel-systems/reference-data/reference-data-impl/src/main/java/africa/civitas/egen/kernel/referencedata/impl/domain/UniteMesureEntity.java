package africa.civitas.egen.kernel.referencedata.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.referencedata.api.domain.CategorieUniteMesure;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "refdata_unite_mesure")
public class UniteMesureEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "nom", nullable = false, unique = true, length = 100)
    public String nom;

    @Column(name = "symbole", nullable = false, length = 20)
    public String symbole;

    @Enumerated(EnumType.STRING)
    @Column(name = "categorie", nullable = false, length = 20)
    public CategorieUniteMesure categorie;

    @Column(name = "facteur_conversion", nullable = false, precision = 20, scale = 6)
    public BigDecimal facteurConversion;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
