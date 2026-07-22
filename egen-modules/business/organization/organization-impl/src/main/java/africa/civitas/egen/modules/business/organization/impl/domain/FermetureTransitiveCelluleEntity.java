package africa.civitas.egen.modules.business.organization.impl.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * Entite JPA de la Fermeture Transitive de Cellule (A2.5). Porte un identifiant
 * technique synthetique pour rester compatible avec PanacheRepositoryBase, mais son
 * unicite reelle repose sur la contrainte (cellule_ancetre_id, cellule_descendante_id)
 * — jamais de Socle de Traçabilite, cette table est entierement derivee.
 */
@Entity
@Table(
        name = "org_fermeture_transitive_cellule",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cellule_ancetre_id", "cellule_descendante_id"}))
public class FermetureTransitiveCelluleEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "cellule_ancetre_id", nullable = false)
    public UUID celluleAncetreId;

    @Column(name = "cellule_descendante_id", nullable = false)
    public UUID celluleDescendanteId;

    @Column(name = "profondeur", nullable = false)
    public int profondeur;
}
