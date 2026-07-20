package africa.civitas.egen.kernel.affiliation.impl.domain;

import africa.civitas.egen.kernel.affiliation.api.domain.MotifFinAffectation;
import africa.civitas.egen.kernel.affiliation.api.domain.QuotiteEngagement;
import africa.civitas.egen.kernel.affiliation.api.domain.StatutAffectation;
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
@Table(name = "aff_affectation")
public class AffectationEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "personne_id", nullable = false)
    public UUID personneId;

    @Column(name = "cellule_id", nullable = false)
    public UUID celluleId;

    @Column(name = "mandat_id", nullable = false)
    public UUID mandatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "quotite_engagement", nullable = false, length = 20)
    public QuotiteEngagement quotiteEngagement;

    @Column(name = "date_debut", nullable = false)
    public LocalDate dateDebut;

    @Column(name = "date_fin")
    public LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 30)
    public StatutAffectation statut;

    @Enumerated(EnumType.STRING)
    @Column(name = "motif_fin", length = 20)
    public MotifFinAffectation motifFin;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
