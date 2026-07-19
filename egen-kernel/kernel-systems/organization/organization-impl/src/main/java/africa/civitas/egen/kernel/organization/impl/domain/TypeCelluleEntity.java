package africa.civitas.egen.kernel.organization.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.organization.api.domain.StatutTypeCellule;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "org_type_cellule")
public class TypeCelluleEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "lexique_id", nullable = false)
    public UUID lexiqueId;

    @Column(name = "libelle", nullable = false, length = 100)
    public String libelle;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    public String description;

    @Column(name = "niveau_indicatif", nullable = false)
    public int niveauIndicatif;

    @ElementCollection
    @CollectionTable(name = "org_type_cellule_parents_autorises", joinColumns = @JoinColumn(name = "type_cellule_id"))
    @Column(name = "type_cellule_parent_id", nullable = false)
    public List<UUID> typesParentsAutorisesIds = new ArrayList<>();

    @Column(name = "type_cellule_modele_origine_id")
    public UUID typeCelluleModeleOrigineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    public StatutTypeCellule statut;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
