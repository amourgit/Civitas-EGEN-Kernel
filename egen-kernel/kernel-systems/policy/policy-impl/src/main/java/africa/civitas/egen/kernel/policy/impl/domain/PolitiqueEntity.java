package africa.civitas.egen.kernel.policy.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.kernel.policy.api.domain.DomainePolitique;
import africa.civitas.egen.kernel.policy.api.domain.StatutPolitique;
import africa.civitas.egen.kernel.sdk.contexte.ContexteNature;
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
@Table(name = "pol_politique")
public class PolitiqueEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "contexte_id", nullable = false)
    public UUID contexteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contexte_nature", nullable = false, length = 20)
    public ContexteNature contexteNature;

    @Enumerated(EnumType.STRING)
    @Column(name = "domaine", nullable = false, length = 30)
    public DomainePolitique domaine;

    @Column(name = "nom_regle", nullable = false, length = 150)
    public String nomRegle;

    @Column(name = "valeur", nullable = false, columnDefinition = "text")
    public String valeur;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    public StatutPolitique statut;

    @Column(name = "date_entree_vigueur", nullable = false)
    public LocalDate dateEntreeVigueur;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
