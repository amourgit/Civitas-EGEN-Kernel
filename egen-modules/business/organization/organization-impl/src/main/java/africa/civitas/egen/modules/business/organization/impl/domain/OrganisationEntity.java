package africa.civitas.egen.modules.business.organization.impl.domain;

import africa.civitas.egen.kernel.jpasupport.tracabilite.TracabiliteEmbeddable;
import africa.civitas.egen.modules.business.organization.api.domain.StatutOrganisation;
import africa.civitas.egen.modules.business.organization.api.domain.TypeOrganisation;
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
@Table(name = "org_organisation")
public class OrganisationEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "raison_sociale", nullable = false, length = 200)
    public String raisonSociale;

    @Column(name = "sigle", nullable = false, length = 30)
    public String sigle;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_organisation", nullable = false, length = 20)
    public TypeOrganisation typeOrganisation;

    @Column(name = "secteur_activite_principal", nullable = false, length = 150)
    public String secteurActivitePrincipal;

    @Column(name = "code_pays_rattachement_juridique", nullable = false, length = 2)
    public String codePaysRattachementJuridique;

    @Column(name = "identifiant_juridique", nullable = false, unique = true, length = 100)
    public String identifiantJuridique;

    @Column(name = "code_devise_reference", nullable = false, length = 3)
    public String codeDeviseReference;

    @ElementCollection
    @CollectionTable(name = "org_organisation_langues_officielles", joinColumns = @JoinColumn(name = "organisation_id"))
    @Column(name = "code_langue", nullable = false, length = 10)
    public List<String> codesLanguesOfficielles = new ArrayList<>();

    @Column(name = "identifiant_fuseau_horaire_reference", nullable = false, length = 100)
    public String identifiantFuseauHoraireReference;

    @Column(name = "modele_sectoriel_origine_id")
    public UUID modeleSectorielOrigineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut", nullable = false, length = 20)
    public StatutOrganisation statut;

    @Column(name = "date_adhesion", nullable = false)
    public LocalDate dateAdhesion;

    @Column(name = "url_identite_visuelle", length = 500)
    public String urlIdentiteVisuelle;

    @Embedded
    public TracabiliteEmbeddable tracabilite;
}
