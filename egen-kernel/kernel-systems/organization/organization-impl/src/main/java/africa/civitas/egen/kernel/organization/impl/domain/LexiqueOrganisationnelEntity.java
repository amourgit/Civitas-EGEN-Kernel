package africa.civitas.egen.kernel.organization.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "org_lexique_organisationnel")
public class LexiqueOrganisationnelEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "organisation_id", nullable = false)
    public UUID organisationId;

    @Column(name = "nom", nullable = false, length = 150)
    public String nom;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    public String description;

    @Column(name = "modele_sectoriel_origine_id")
    public UUID modeleSectorielOrigineId;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
