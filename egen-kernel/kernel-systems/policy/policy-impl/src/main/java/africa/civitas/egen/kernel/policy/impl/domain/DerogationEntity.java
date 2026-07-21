package africa.civitas.egen.kernel.policy.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pol_derogation")
public class DerogationEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "politique_id", nullable = false)
    public UUID politiqueId;

    @Column(name = "cellule_derogatoire_id", nullable = false)
    public UUID celluleDerogatoireId;

    @Column(name = "valeur", nullable = false, columnDefinition = "text")
    public String valeur;

    @Column(name = "justification", nullable = false, columnDefinition = "text")
    public String justification;

    @Column(name = "date_entree_vigueur", nullable = false)
    public LocalDate dateEntreeVigueur;

    @Column(name = "date_fin")
    public LocalDate dateFin;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
