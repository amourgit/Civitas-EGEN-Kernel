package africa.civitas.egen.modules.business.organization.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.modules.business.organization.api.domain.StatutCellule;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Entite JPA de la Cellule (A2.4). La reference au parent est un simple UUID, jamais
 * une relation JPA @ManyToOne — l'arbre est navigue exclusivement via la Fermeture
 * Transitive (FermetureTransitiveCelluleEntity), pas par des jointures recursives.
 */
@Entity
@Table(name = "org_cellule")
public class CelluleEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "organisation_id", nullable = false)
    public UUID organisationId;

    @Column(name = "cellule_parent_id")
    public UUID celluleParentId;

    @Column(name = "type_cellule_id", nullable = false)
    public UUID typeCelluleId;

    @Column(name = "nom", nullable = false, length = 200)
    public String nom;

    @Column(name = "code_interne", nullable = false, length = 50)
    public String codeInterne;

    @Column(name = "description", columnDefinition = "text")
    public String description;

    @Column(name = "code_pays_localisation", length = 2)
    public String codePaysLocalisation;

    @Column(name = "adresse_physique", length = 300)
    public String adressePhysique;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    public StatutCellule statut;

    @Column(name = "valid_du", nullable = false)
    public LocalDate validDu;

    @Column(name = "valid_au")
    public LocalDate validAu;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
