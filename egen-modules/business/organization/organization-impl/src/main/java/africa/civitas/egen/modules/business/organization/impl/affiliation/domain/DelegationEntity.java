package africa.civitas.egen.modules.business.organization.impl.affiliation.domain;

import africa.civitas.egen.modules.business.organization.api.affiliation.domain.EtendueDelegation;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.MotifDelegation;
import africa.civitas.egen.modules.business.organization.api.affiliation.domain.StatutDelegation;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
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

@Entity
@Table(name = "aff_delegation")
public class DelegationEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "affectation_origine_id", nullable = false)
    public UUID affectationOrigineId;

    @Column(name = "personne_beneficiaire_id", nullable = false)
    public UUID personneBeneficiaireId;

    @Enumerated(EnumType.STRING)
    @Column(name = "etendue", nullable = false, length = 20)
    public EtendueDelegation etendue;

    @Column(name = "actions_couvertes", columnDefinition = "text")
    public String actionsCouvertes;

    @Column(name = "date_debut", nullable = false)
    public LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    public LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "motif", nullable = false, length = 20)
    public MotifDelegation motif;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    public StatutDelegation statut;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
