package africa.civitas.egen.modules.business.organization.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.modules.business.organization.api.domain.NatureTutelle;
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
@Table(name = "org_tutelle")
public class TutelleEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "cellule_racine_id", nullable = false)
    public UUID celluleRacineId;

    @Column(name = "organisation_id", nullable = false)
    public UUID organisationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false, length = 20)
    public NatureTutelle nature;

    @Column(name = "tutelle_principale", nullable = false)
    public boolean tutellePrincipale;

    @Column(name = "date_debut", nullable = false)
    public LocalDate dateDebut;

    @Column(name = "date_fin")
    public LocalDate dateFin;

    @Column(name = "acte_justificatif_ref")
    public UUID acteJustificatifRef;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
