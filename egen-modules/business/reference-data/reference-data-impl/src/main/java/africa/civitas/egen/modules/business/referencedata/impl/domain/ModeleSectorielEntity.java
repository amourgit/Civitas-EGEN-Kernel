package africa.civitas.egen.modules.business.referencedata.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.modules.business.referencedata.api.domain.StatutModeleSectoriel;
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
@Table(name = "refdata_modele_sectoriel")
public class ModeleSectorielEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "nom_secteur", nullable = false, length = 150)
    public String nomSecteur;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    public String description;

    @Column(name = "version_modele", nullable = false, length = 30)
    public String versionModele;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    public StatutModeleSectoriel statut;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
