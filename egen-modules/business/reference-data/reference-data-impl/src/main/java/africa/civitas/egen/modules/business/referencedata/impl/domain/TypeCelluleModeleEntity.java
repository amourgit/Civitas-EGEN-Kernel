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
@Table(name = "refdata_type_cellule_modele")
public class TypeCelluleModeleEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "modele_sectoriel_id", nullable = false)
    public UUID modeleSectorialId;

    @Column(name = "libelle_metier_suggere", nullable = false, length = 100)
    public String libelleMetierSuggere;

    @Column(name = "niveau_indicatif_suggere", nullable = false)
    public int niveauIndicatifSuggere;

    @Column(name = "type_parent_suggere_id")
    public UUID typeParentSuggereId;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
