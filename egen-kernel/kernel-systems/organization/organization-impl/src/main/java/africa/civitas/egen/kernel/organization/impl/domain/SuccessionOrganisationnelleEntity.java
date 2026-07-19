package africa.civitas.egen.kernel.organization.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.organization.api.domain.NatureSuccession;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "org_succession_organisationnelle")
public class SuccessionOrganisationnelleEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @ElementCollection
    @CollectionTable(name = "org_succession_origine", joinColumns = @JoinColumn(name = "succession_id"))
    @Column(name = "cellule_id", nullable = false)
    public List<UUID> celluleOrigineIds = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "org_succession_heritiere", joinColumns = @JoinColumn(name = "succession_id"))
    @Column(name = "cellule_id", nullable = false)
    public List<UUID> celluleHeritiereIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false, length = 30)
    public NatureSuccession nature;

    @Column(name = "date_effet", nullable = false)
    public LocalDate dateEffet;

    @Column(name = "motif_decision_reference", columnDefinition = "text")
    public String motifDecisionReference;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
