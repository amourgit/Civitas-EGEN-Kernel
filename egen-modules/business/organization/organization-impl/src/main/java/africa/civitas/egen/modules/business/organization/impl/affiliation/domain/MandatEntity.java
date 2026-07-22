package africa.civitas.egen.modules.business.organization.impl.affiliation.domain;

import africa.civitas.egen.modules.business.organization.api.affiliation.domain.StatutMandat;
import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
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
@Table(name = "aff_mandat")
public class MandatEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "lexique_mandats_id", nullable = false)
    public UUID lexiqueMandatsId;

    @Column(name = "libelle", nullable = false, length = 100)
    public String libelle;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    public String description;

    @Column(name = "niveau_autorite_indicatif", nullable = false)
    public int niveauAutoriteIndicatif;

    @ElementCollection
    @CollectionTable(name = "aff_mandat_supervises", joinColumns = @JoinColumn(name = "mandat_id"))
    @Column(name = "mandat_supervise_id", nullable = false)
    public List<UUID> mandatsSupervisesIds = new ArrayList<>();

    @Column(name = "mandat_modele_origine_id")
    public UUID mandatModeleOrigineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    public StatutMandat statut;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
