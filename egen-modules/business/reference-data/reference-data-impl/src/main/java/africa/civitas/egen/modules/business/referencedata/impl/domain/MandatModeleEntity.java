package africa.civitas.egen.modules.business.referencedata.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "refdata_mandat_modele")
public class MandatModeleEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "modele_sectoriel_id", nullable = false)
    public UUID modeleSectorialId;

    @Column(name = "libelle_suggere", nullable = false, length = 100)
    public String libelleSuggere;

    @Column(name = "niveau_autorite_indicatif", nullable = false)
    public int niveauAutoriteIndicatif;

    @Column(name = "description_responsabilites", nullable = false, columnDefinition = "text")
    public String descriptionResponsabilites;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
